package com.example.my.data

import android.content.Context
import com.example.my.model.*
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 因果推断模块：倾向得分加权 + 双稳健估计
 * 去除曝光偏差、流行度偏差，估计用户对物品的真实兴趣
 */
object CausalInference {
    private const val EPS = 1e-8f
    private const val MAX_FEATURES = 5
    private const val LEARNING_RATE = 0.01f
    private const val EPOCHS = 50

    /**
     * 轻量逻辑回归模型，用于移动端在线训练
     * 特征：曝光位置、流行度、用户活跃度、类别编码、标签匹配度
     */
    private class LogisticRegression(private val numFeatures: Int = MAX_FEATURES) {
        private var weights = FloatArray(numFeatures) { 0f }
        private var bias = 0f

        /**
         * sigmoid激活函数
         */
        private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))

        /**
         * 提取特征向量
         */
        private fun extractFeatures(ctx: ExposureContext): FloatArray {
            // 特征归一化：位置取倒数（位置越靠后值越小）、流行度、活跃度、类别匹配、标签匹配
            val posFeature = 1f / (ctx.position + 1f)
            val popFeature = ctx.popularity
            val actFeature = ctx.userActivity
            val catFeature = if (ctx.itemCategory.isNotEmpty()) 1f else 0f
            val tagFeature = min(ctx.userTags.size.toFloat() / 5f, 1f)
            return floatArrayOf(posFeature, popFeature, actFeature, catFeature, tagFeature)
        }

        /**
         * 训练模型
         */
        fun train(samples: List<Pair<ExposureContext, Float>>) {
            repeat(EPOCHS) {
                samples.forEach { (ctx, label) ->
                    val x = extractFeatures(ctx)
                    val pred = predict(ctx)
                    val error = pred - label
                    // 更新权重
                    for (i in weights.indices) {
                        weights[i] -= LEARNING_RATE * error * x[i]
                    }
                    bias -= LEARNING_RATE * error
                }
            }
        }

        /**
         * 预测概率
         */
        fun predict(ctx: ExposureContext): Float {
            val x = extractFeatures(ctx)
            var sum = bias
            for (i in weights.indices) {
                sum += weights[i] * x[i]
            }
            return sigmoid(sum)
        }

        /**
         * 序列化模型到JSON
         */
        fun toJson(): JSONObject = JSONObject()
            .put("weights", JSONArray(weights.toList()))
            .put("bias", bias)

        /**
         * 从JSON反序列化模型
         */
        fun fromJson(json: JSONObject) {
            val weightsArr = json.getJSONArray("weights")
            weights = FloatArray(weightsArr.length()) { weightsArr.getDouble(it).toFloat() }
            bias = json.getDouble("bias").toFloat()
        }
    }

    // 倾向得分模型：估计物品被曝光的概率p(u,i,c)
    private val propensityModel = LogisticRegression()
    // 结果预测模型：估计用户在无偏差下的反馈概率y_hat(u,i,c)
    private val outcomeModel = LogisticRegression()

    // 行为数据缓存
    private val exposures = mutableListOf<Exposure>()
    private val feedbacks = mutableListOf<Feedback>()
    private const val MAX_SAMPLES = 1000 // 最多缓存1000条样本，防止占用过多内存

    /**
     * 记录曝光行为
     */
    fun recordExposure(exposure: Exposure) {
        exposures.add(exposure)
        if (exposures.size > MAX_SAMPLES) {
            exposures.removeFirst()
        }
    }

    /**
     * 记录用户反馈
     */
    fun recordFeedback(feedback: Feedback) {
        feedbacks.add(feedback)
        if (feedbacks.size > MAX_SAMPLES) {
            feedbacks.removeFirst()
        }
    }

    /**
     * 训练模型：从历史行为中学习倾向得分和结果预测模型
     */
    suspend fun train(context: Context) = withContext(Dispatchers.IO) {
        // 构建训练样本：曝光 + 是否有反馈
        val propensitySamples = mutableListOf<Pair<ExposureContext, Float>>()
        val outcomeSamples = mutableListOf<Pair<ExposureContext, Float>>()

        exposures.forEach { exp ->
            // 查找对应反馈
            val feedback = feedbacks.find { it.exposureId == exp.itemId }
            val label = if (feedback != null) {
                // 不同反馈类型权重不同：点击=0.5，收藏=1.0，添加到行程=1.5
                when (feedback.type) {
                    FeedbackType.CLICK -> 0.5f
                    FeedbackType.STAY -> min(feedback.value / 10f, 0.8f)
                    FeedbackType.LIKE -> 0.7f
                    FeedbackType.COLLECT -> 1f
                    FeedbackType.ADD_TO_PLAN -> 1.5f
                }
            } else 0f

            propensitySamples.add(exp.context to if (label > 0) 1f else 0f)
            outcomeSamples.add(exp.context to min(label, 1f))
        }

        if (propensitySamples.size >= 10) { // 至少10条样本才训练
            propensityModel.train(propensitySamples)
            outcomeModel.train(outcomeSamples)
            saveModels(context)
        }
    }

    /**
     * 计算因果得分：去偏后的真实兴趣
     * 公式：C = y_hat + (Y - p) / (p + EPS)
     * @param Y 真实反馈值，无反馈时为0
     * @param ctx 曝光上下文
     */
    fun computeCausalScore(Y: Float, ctx: ExposureContext): CausalScore {
        val p = max(propensityModel.predict(ctx), EPS)
        val yHat = outcomeModel.predict(ctx)
        val causal = yHat + (Y - p) / (p + EPS)
        // 归一化到[0,1]区间，方便排序
        val normalizedCausal = max(min(causal, 1f), 0f)
        return CausalScore(normalizedCausal, yHat, p)
    }

    /**
     * 对推荐列表进行因果重排序，优先展示用户真实感兴趣的内容
     * 避免"热门物品因为曝光多被点击多所以排前面"的偏差
     * @param items 待排序的物品列表
     * @param getContext 从物品中获取曝光上下文的函数
     * @param getFeedback 获取用户对该物品的历史反馈（无则为0）
     */
    fun <T> reRank(
        items: List<T>,
        getContext: (T, Int) -> ExposureContext, // Int是当前排序位置
        getFeedback: (T) -> Float = { 0f }
    ): List<T> {
        if (items.isEmpty()) return items

        // 计算每个物品的因果得分
        val scoredItems = items.mapIndexed { idx, item ->
            val ctx = getContext(item, idx)
            val Y = getFeedback(item)
            val score = computeCausalScore(Y, ctx)
            item to score.causalScore
        }

        // 按因果得分降序排序，得分相同的保持原有顺序
        return scoredItems.sortedByDescending { it.second }.map { it.first }
    }

    /**
     * 保存模型到本地文件
     */
    private suspend fun saveModels(context: Context) = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "causal_models.json")
        val json = JSONObject()
            .put("propensity", propensityModel.toJson())
            .put("outcome", outcomeModel.toJson())
            .put("exposures", JSONArray(exposures.map { it.toJson() }))
            .put("feedbacks", JSONArray(feedbacks.map { it.toJson() }))
        file.writeText(json.toString())
    }

    /**
     * 从本地加载模型
     */
    suspend fun loadModels(context: Context) = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "causal_models.json")
        if (!file.exists()) return@withContext
        runCatching {
            val json = JSONObject(file.readText())
            propensityModel.fromJson(json.getJSONObject("propensity"))
            outcomeModel.fromJson(json.getJSONObject("outcome"))
            // 加载历史行为数据
            val expArr = json.getJSONArray("exposures")
            for (i in 0 until expArr.length()) {
                exposures.add(exposureFromJson(expArr.getJSONObject(i)))
            }
            val fbArr = json.getJSONArray("feedbacks")
            for (i in 0 until fbArr.length()) {
                feedbacks.add(feedbackFromJson(fbArr.getJSONObject(i)))
            }
        }
    }

    // 扩展方法：序列化行为数据
    private fun Exposure.toJson(): JSONObject = JSONObject()
        .put("userId", userId)
        .put("itemId", itemId)
        .put("context", context.toJson())
        .put("timestamp", timestamp)

    private fun ExposureContext.toJson(): JSONObject = JSONObject()
        .put("position", position)
        .put("popularity", popularity)
        .put("userActivity", userActivity)
        .put("itemCategory", itemCategory)
        .put("userTags", JSONArray(userTags))

    private fun Feedback.toJson(): JSONObject = JSONObject()
        .put("exposureId", exposureId)
        .put("type", type.name)
        .put("value", value)
        .put("timestamp", timestamp)

    // 反序列化
    private fun exposureFromJson(json: JSONObject): Exposure = Exposure(
        userId = json.getString("userId"),
        itemId = json.getString("itemId"),
        context = exposureContextFromJson(json.getJSONObject("context")),
        timestamp = json.getLong("timestamp")
    )

    private fun exposureContextFromJson(json: JSONObject): ExposureContext = ExposureContext(
        position = json.getInt("position"),
        popularity = json.getDouble("popularity").toFloat(),
        userActivity = json.getDouble("userActivity").toFloat(),
        itemCategory = json.getString("itemCategory"),
        userTags = List(json.getJSONArray("userTags").length()) { json.getJSONArray("userTags").getString(it) }
    )

    private fun feedbackFromJson(json: JSONObject): Feedback = Feedback(
        exposureId = json.getString("exposureId"),
        type = FeedbackType.valueOf(json.getString("type")),
        value = json.getDouble("value").toFloat(),
        timestamp = json.getLong("timestamp")
    )
}
