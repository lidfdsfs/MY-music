package com.example.my.data.ai

import java.util.Locale

/**
 * 第二层：轻量意图分类/打分器。
 *
 * 不加载额外的 embedding 模型，也不发网络请求；用可解释的词面触发短语
 * （实时/事实词 vs 推理/创作/加工/本地词）判断这句话更接近哪种意图，并给出
 * 置信度。只有置信度足够高时才由本层直接裁决，剩下低置信度的才升级到第三层
 * 的大模型判断。这样既能拦住「为什么天空是蓝色」这类纯知识问题，也不会把
 * 「最近有什么新政策」误判成不需要检索。
 *
 * 这一层不改变搜索实现、结果清洗和来源展示，只输出「搜不搜」的建议。
 */
object SearchIntentScorer {

    enum class Intent {
        RETRIEVAL,     // 需要外部实时/事实信息
        REASONING,     // 常识解释、原理、计算
        COMPARISON,    // 对比/区别
        CREATIVE,      // 写作/创作
        TRANSFORM,     // 翻译/润色/总结
        LOCAL,         // App 内自己的数据
        CHAT,          // 寒暄
        UNKNOWN
    }

    data class Score(
        val search: Boolean,
        val confidence: Double,
        val reason: String,
        val intent: Intent = Intent.UNKNOWN
    )

    /** 正分：越像需要外部实时信息，权重越高。 */
    private val RETRIEVAL = listOf(
        "最新" to 2.0, "实时" to 2.0, "今天" to 1.5, "今日" to 1.5,
        "明天" to 1.5, "现在" to 1.5, "当前" to 1.5, "最近" to 1.5,
        "今年" to 1.0, "新闻" to 2.0, "公告" to 2.0, "政策" to 2.0,
        "规定" to 1.5, "新规" to 2.0, "发布" to 1.5, "上线" to 1.5,
        "价格" to 2.0, "多少钱" to 2.0, "票价" to 2.0, "门票" to 2.0,
        "机票" to 2.0, "车票" to 2.0, "高铁票" to 2.0, "航班" to 2.0,
        "余票" to 2.0, "车次" to 1.5, "营业时间" to 2.0, "开放时间" to 2.0,
        "汇率" to 2.0, "股价" to 2.0, "比分" to 2.0, "赛程" to 2.0,
        "天气" to 2.0, "气温" to 2.0, "温度" to 1.5, "下雨" to 1.5,
        "怎么去" to 1.5, "交通" to 1.5, "路线" to 1.5, "限行" to 2.0,
        "酒店" to 1.5, "民宿" to 1.5, "住宿" to 1.5, "餐厅" to 1.5,
        "景点" to 1.5, "攻略" to 1.5, "行程" to 1.5, "美食" to 1.5,
        "小吃" to 1.5, "好玩" to 1.5, "值得去" to 1.5, "必去" to 1.5,
        "推荐" to 1.5, "评价" to 1.0, "口碑" to 1.0, "测评" to 1.5,
        "哪家" to 1.5, "哪里" to 1.0, "查一下" to 1.5, "帮我查" to 1.5,
        "搜一下" to 1.5, "来源" to 1.5, "出处" to 1.5, "引用" to 1.5,
        "参考文献" to 2.0, "核实" to 1.5, "求证" to 1.5, "真的吗" to 1.5,
        // 人物/公司职务变动是强事实断言，必须按最新新闻核对
        "不再担任" to 2.0, "不在担任" to 2.0, "卸任" to 2.0, "辞任" to 2.0,
        "辞去" to 2.0, "辞职" to 1.5, "离职" to 1.5, "接任" to 2.0,
        "出任" to 1.5, "任命" to 1.5, "换帅" to 2.0, "罢免" to 2.0, "免职" to 2.0
    )

    /** 推理/解释：通常凭常识就能回答，不需要外部实时信息。 */
    private val REASONING = listOf(
        "为什么" to 2.0, "解释" to 2.0, "原理" to 2.0, "定义" to 1.5,
        "是什么" to 2.0, "含义" to 1.5, "原因" to 1.5, "分析" to 1.5,
        "影响" to 1.0, "意味着" to 1.0, "公式" to 2.0, "证明" to 2.0,
        "计算" to 2.0, "换算" to 2.0, "怎么做" to 1.5, "如何实现" to 2.0
    )

    /** 对比：单独出现时多为常识比较；和实时/价格词同现时由正分胜出。 */
    private val COMPARISON = listOf(
        "对比" to 1.5, "比较" to 1.5, "区别" to 1.5, "哪个好" to 1.5,
        "哪个更" to 1.5, "谁更好" to 1.5, "优缺点" to 1.5, "vs" to 1.0
    )

    /** 创作：写文案、诗、故事等，不需要外部检索。 */
    private val CREATIVE = listOf(
        "写" to 1.5, "创作" to 1.5, "生成" to 1.5, "续写" to 1.5,
        "起个标题" to 1.5, "取个名字" to 1.5, "文案" to 1.5, "诗" to 1.5,
        "故事" to 1.5, "歌词" to 1.5, "剧本" to 1.5, "祝福" to 1.5,
        "朋友圈" to 1.0
    )

    /** 文本加工：翻译、润色、总结等，不需要外部检索。 */
    private val TRANSFORM = listOf(
        "翻译" to 2.0, "润色" to 2.0, "改写" to 1.5, "总结" to 1.5,
        "摘要" to 1.5, "提纲" to 1.5, "纠错" to 1.5, "提炼" to 1.5
    )

    /** App 内自己的数据，走本地能力。 */
    private val LOCAL = listOf(
        "我的" to 2.0, "我收藏" to 2.0, "我点赞" to 2.0, "我去过" to 2.0,
        "我的行程" to 2.0, "我的足迹" to 2.0, "我的攻略" to 2.0,
        "我的计划" to 2.0, "我的笔记" to 2.0, "我的数据" to 2.0,
        "这个app" to 2.0, "本app" to 2.0, "软件设置" to 2.0
    )

    private val CHAT = listOf(
        "你好" to 1.0, "您好" to 1.0, "谢谢" to 1.0, "在吗" to 1.0,
        "早上好" to 1.0, "晚安" to 1.0, "再见" to 1.0
    )

    private val MATH = Regex("\\d+\\s*[+\\-*/×÷]\\s*\\d+")
    private val CODE_BLOCK = Regex("```|fun\\s+\\w+|class\\s+\\w+|SELECT\\s+.+FROM", RegexOption.IGNORE_CASE)

    /** 返回 null = 分数不够明确，交给第三层模型。 */
    fun score(text: String): Score? {
        val t = text.trim().lowercase(Locale.ROOT)
        if (t.length < 2) return null
        val scores = linkedMapOf<Intent, Double>()
        add(scores, Intent.RETRIEVAL, RETRIEVAL, t)
        add(scores, Intent.REASONING, REASONING, t)
        add(scores, Intent.COMPARISON, COMPARISON, t)
        add(scores, Intent.CREATIVE, CREATIVE, t)
        add(scores, Intent.TRANSFORM, TRANSFORM, t)
        add(scores, Intent.LOCAL, LOCAL, t)
        add(scores, Intent.CHAT, CHAT, t)
        if (MATH.containsMatchIn(t)) {
            scores[Intent.REASONING] = (scores[Intent.REASONING] ?: 0.0) + 2.0
        }
        if (CODE_BLOCK.containsMatchIn(t)) {
            scores[Intent.REASONING] = (scores[Intent.REASONING] ?: 0.0) + 2.0
        }
        if (scores.isEmpty()) return null

        val total = scores.values.sum()
        val top = scores.maxByOrNull { it.value } ?: return null
        val second = scores.entries
            .filter { it.key != top.key }
            .maxOfOrNull { it.value } ?: 0.0
        if (total <= 0.0) return null
        // 置信度同时看「证据总量」和「第一名领先第二名多少」。
        // 只命中一个弱词时置信度会低于阈值，交给模型；命中多个一致特征才直接裁决。
        val evidence = (total / 3.0).coerceIn(0.0, 1.0)
        val margin = ((top.value - second).coerceAtLeast(0.0) / total).coerceIn(0.0, 1.0)
        val confidence = (evidence * (0.5 + 0.5 * margin)).coerceIn(0.0, 1.0)
        if (confidence < 0.60) return null

        val intent = top.key
        val search = intent == Intent.RETRIEVAL
        val reason = if (search) {
            "轻量意图=${intent.label}，命中实时/事实特征（置信度 ${"%.2f".format(confidence)}）"
        } else {
            "轻量意图=${intent.label}，可直接凭常识/本地数据回答（置信度 ${"%.2f".format(confidence)}）"
        }
        return Score(search, confidence, reason, intent)
    }

    private fun add(
        scores: MutableMap<Intent, Double>,
        intent: Intent,
        cues: List<Pair<String, Double>>,
        text: String
    ) {
        var sum = 0.0
        cues.forEach { (word, weight) -> if (text.contains(word)) sum += weight }
        if (sum > 0.0) scores[intent] = (scores[intent] ?: 0.0) + sum
    }

    private val Intent.label: String
        get() = when (this) {
            Intent.RETRIEVAL -> "检索"
            Intent.REASONING -> "推理"
            Intent.COMPARISON -> "对比"
            Intent.CREATIVE -> "创作"
            Intent.TRANSFORM -> "文本加工"
            Intent.LOCAL -> "本地数据"
            Intent.CHAT -> "寒暄"
            Intent.UNKNOWN -> "未知"
        }
}
