package com.example.my.data.ai

import android.content.Context
import com.example.my.data.Locator
import com.example.my.data.music.MusicPlayer
import com.example.my.state.AppState
import com.example.my.state.ExtraScreen

/**
 * 现场快照：让 AI 知道「用户现在在哪一页、正看着什么」。
 *
 * 「接入每一个地方」的真正含义不是每个页面放一个聊天框，而是任何位置唤起 AI 时
 * 都自带现场。没有这块，「这个攻略」「刚才那条」「从这里出发」永远只能靠猜。
 *
 * 隐私约束：**精确坐标绝不写进 system / 绝不发往云端**，定位只给「有无 + 精度等级」；
 * 足迹/攻略只给标题级摘要，不给备注全文与照片路径。
 */
object AppContextSnapshot {

    fun render(state: AppState, app: Context): String = buildString {
        appendLine("【用户当前现场（每次对话实时生成）】")

        // 位置：底部 Tab / 全屏页 / 叠加层
        append("所在位置：${state.activeTab.label}")
        if (state.screen != ExtraScreen.NONE) append(" → 全屏页 ${screenName(state.screen)}")
        appendLine()
        if (state.activeTab.name == "MAP") {
            appendLine("足迹页分段：${if (state.footprintMode == 1) "旅行回忆" else "地图足迹"}")
        }
        val layers = mutableListOf<String>()
        if (state.showUpload) layers.add("发布页")
        if (state.mapPickOpen) layers.add("地图选点")
        if (state.showPlanSheet) layers.add("新建计划弹窗")
        if (state.showAiTripSheet) layers.add("AI 行程弹窗")
        if (state.tripDetailId != null) layers.add("行程详情")
        if (layers.isNotEmpty()) appendLine("当前叠加层：${layers.joinToString("、")}")

        // 正在看的内容：只给标题，不给正文
        state.currentGuideId?.let { id ->
            state.guides.firstOrNull { it.id == id }?.let {
                appendLine("正在看攻略：《${it.title}》（${it.city}）")
            }
        }
        state.currentRouteId?.let { id ->
            state.routes.firstOrNull { it.id == id }?.let { appendLine("正在看路线：《${it.title}》") }
        }
        state.tripDetailId?.let { id ->
            state.tripPlans.firstOrNull { it.id == id }?.let {
                appendLine("正在看行程：《${it.name}》${it.destination} ${it.startDate}")
            }
        }

        // 环境与数据概况
        val w = state.weatherNow
        appendLine(
            "天气：${state.weatherCity}" +
                if (w != null) "，缓存实况 ${w.temp}℃ ${w.desc}" else "（暂无实况缓存）"
        )
        appendLine(
            "数据概况：足迹 ${state.footprints.size} 条 / 攻略 ${state.guides.size} 篇 / " +
                "行程 ${state.tripPlans.size} 个 / 路线 ${state.routes.size} 条"
        )
        val fix = runCatching { Locator.lastKnown(app) }.getOrNull()
        appendLine(
            if (fix == null) "定位：无可用定位（问「附近」要先反问城市）"
            else "定位：有（精度约 ${fix.accuracyM.toInt()} 米，坐标不提供）"
        )
        if (state.trackingOn) appendLine("轨迹录制中")

        // 当前实际使用的模型：对话框里选哪个就是哪个，不再有模式概念
        appendLine(
            "当前对话模型：${state.activeModelName}；界面语言：${state.language}；" +
                "AI 回复语言：${state.aiLanguage}；主题：${when (state.themeMode) { 1 -> "浅色"; 2 -> "深色"; else -> "跟随系统" }}；" +
                "长期记忆：${if (state.aiMemoryEnabled) "开" else "关"}"
        )

        state.aiEntryHint?.takeIf { it.isNotBlank() }?.let {
            appendLine("本次由就地入口唤起，来源：$it")
        }
        MusicPlayer.current?.let { song ->
            appendLine(
                "音乐：" + (if (MusicPlayer.isPlaying) "正在播放" else "已暂停") +
                    "《${song.title}》- ${song.artist}；队列 ${MusicPlayer.currentQueue.size} 首"
            )
        } ?: appendLine("音乐：当前没有播放歌曲")
        appendLine(
            "注意：用户说「这个」「刚才那条」「这里」「把它改成…」时，按上面的现场理解；" +
                "现场信息不足就反问，不要凭空猜测具体是哪一条。"
        )
    }.trim()

    /** 就地入口显示的一行现场标签：不暴露具体坐标，只说「在哪、在看什么」 */
    fun sceneLabel(state: AppState): String {
        val g = state.currentGuideId?.let { id -> state.guides.firstOrNull { it.id == id } }
        val t = state.tripDetailId?.let { id -> state.tripPlans.firstOrNull { it.id == id } }
        return when {
            g != null -> "攻略详情 · 《${g.title}》"
            t != null -> "行程详情 · 《${t.name}》"
            state.showUpload -> "发布页"
            state.mapPickOpen -> "地图选点"
            state.screen != ExtraScreen.NONE -> screenName(state.screen)
            state.activeTab.name == "MAP" ->
                if (state.footprintMode == 1) "足迹 · 旅行回忆" else "足迹 · 地图"
            else -> state.activeTab.label
        }
    }

    /**
     * 页面语义提示：页面只声明「在这里适合问什么」，不各自接模型。
     * 就地卡片、AI 助手页顶部提示条共用这一份，避免每个页面硬编码一套问题。
     */
    fun hintsFor(state: AppState): List<String> {
        val g = state.currentGuideId?.let { id -> state.guides.firstOrNull { it.id == id } }
        val t = state.tripDetailId?.let { id -> state.tripPlans.firstOrNull { it.id == id } }
        return when {
            g != null -> listOf(
                "总结一下《${g.title}》",
                "${g.city}还有哪些类似玩法",
                "把这篇攻略排成我的行程"
            )
            t != null -> listOf(
                "检查《${t.name}》的安排合不合理",
                "${t.destination}这几天天气怎么样",
                "给这个行程补一份出行清单"
            )
            state.showUpload -> listOf("帮这段经历写个文案", "该选什么标签", "把这里记成足迹")
            state.mapPickOpen -> listOf("这附近有什么值得去", "从这里出发怎么安排")
            state.screen == ExtraScreen.WEATHER -> listOf(
                "今天适合出门吗", "按这个天气推荐去处", "帮我写进今天的行程"
            )
            state.screen == ExtraScreen.MY_PLANS -> listOf(
                "我的行程哪个快到期了", "帮我排一个新的行程", "统计一下我的计划"
            )
            state.screen == ExtraScreen.SETTINGS -> listOf("怎么开启语音播报", "切到本地模型会怎样")
            state.screen == ExtraScreen.CREATIVE -> listOf("生成一张山间瀑布的图片", "生成一段海边日落视频", "帮我写一个适合做壁纸的提示词")
            state.screen == ExtraScreen.SHOPPING -> listOf(
                "这个价格现在值得买吗", "帮我看看哪家更便宜", "同价位有什么替代品"
            )
            state.activeTab.name == "MAP" -> listOf(
                "我去过哪些城市", "按足迹推荐下一站", "帮我整理这段路线"
            )
            else -> listOf("按我的足迹推荐下一站", "帮我规划三天行程", "最近有什么适合去的地方")
        }
    }

    private fun screenName(s: ExtraScreen): String = when (s) {
        ExtraScreen.NONE -> "主页"
        ExtraScreen.SETTINGS -> "设置"
        ExtraScreen.NOTIFICATIONS -> "通知"
        ExtraScreen.GUIDE_CREATE -> "发布攻略"
        ExtraScreen.MY_PLANS -> "我的计划"
        ExtraScreen.WEATHER -> "天气"
        ExtraScreen.MESSAGES -> "私信消息"
        ExtraScreen.USER_PROFILE -> "他人主页"
        ExtraScreen.AI_ASSISTANT -> "AI 助手"
        ExtraScreen.YEAR_REPORT -> "年度报告"
        ExtraScreen.OFFLINE -> "离线"
        ExtraScreen.MUSIC -> "音乐"
        ExtraScreen.TRAVELER_RANK -> "旅行达人"
        ExtraScreen.WEB_SEARCH -> "网页搜索"
        ExtraScreen.COURSE -> "课表"
        ExtraScreen.TICKET_ZONE -> "购票专区"
        ExtraScreen.SHOPPING -> "购物专区"
        ExtraScreen.PRIVACY_VAULT -> "隐私空间"
        ExtraScreen.NEWS -> "资讯"
        ExtraScreen.STOCK -> "股市行情"
        ExtraScreen.TRADE -> "我的交易"
        ExtraScreen.CREATIVE -> "图片视频创作"
        ExtraScreen.DRIVING -> "驾驶模式"
        ExtraScreen.FOOTPRINTS -> "我的足迹"
    }
}
