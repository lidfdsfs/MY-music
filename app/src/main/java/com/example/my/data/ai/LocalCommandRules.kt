package com.example.my.data.ai

import com.example.my.data.CommandIntent

/**
 * 本地指令兜底：不依赖云端模型，也能把「打开深色模式」「关掉震动」「打开设置里的回收站」
 * 这类高频设置/页面指令解析成 [CommandIntent]。
 *
 * 只在这一层拦截**短句 + 明确动词 + 明确对象**，宁可漏判也不误判：
 * 用户说「深色模式好不好看」不该被当成切主题，所以规则里要求出现动词或「模式/主题」这类
 * 明确名词，且整句不超过 30 字。判不出来就返回 null，交回模型那条主链路。
 */
object LocalCommandRules {

    private val VERBS = listOf(
        "打开", "开启", "进入", "切到", "切换到", "切回", "切", "换成", "改成", "改到",
        "换到", "调到", "设置为", "设置成", "设为", "跳到", "关闭", "关掉", "关了", "别"
    )

    private val OPEN_VERBS = listOf("打开", "进入", "切到", "切换到", "跳到", "去打开")

    /** 页面名 → open_screen 的 target */
    private val PAGES = listOf(
        Triple(listOf("股市", "行情", "大盘", "股票", "基金"), "stock", "股市行情"),
        Triple(listOf("我的交易", "交易页", "模拟交易", "模拟盘", "持仓", "委托单"), "trade", "我的交易"),
        Triple(listOf("资讯", "新闻", "热点"), "news", "资讯"),
        Triple(listOf("音乐", "播放器", "歌单"), "music", "音乐"),
        Triple(listOf("课程表", "课表"), "course", "课程表"),
        Triple(listOf("年度报告", "年度总结", "年终"), "year_report", "年度报告"),
        Triple(listOf("离线地图", "地图包", "离线包"), "offline", "离线地图包"),
        Triple(listOf("我的行程", "行程页", "行程列表"), "my_plans", "我的行程"),
        Triple(listOf("我的足迹", "足迹地图", "足迹页"), "map", "我的足迹"),
        Triple(listOf("回忆", "回忆页"), "memory", "回忆"),
        Triple(listOf("通知", "通知消息", "未读"), "notifications", "通知消息"),
        Triple(listOf("站内消息", "私信", "消息页"), "messages", "站内消息"),
        Triple(listOf("创意工坊", "创作专区", "生成图片"), "creative", "创意工坊"),
        Triple(listOf("票务", "买票", "购票"), "ticket_zone", "票务专区"),
        Triple(listOf("旅行人", "排行", "同行人"), "travelers", "常访问的旅行人"),
        Triple(listOf("网页搜索", "搜索页"), "web_search", "网页搜索"),
        Triple(listOf("天气页", "天气预报"), "weather", "天气"),
        Triple(listOf("AI 助手", "AI助手", "助手"), "ai", "AI 助手"),
        Triple(listOf("新建足迹", "记一笔", "添加足迹"), "upload", "新建足迹"),
        Triple(listOf("我的页", "个人主页", "我的"), "profile", "我的"),
        Triple(listOf("发现页", "发现"), "discover", "发现"),
        Triple(listOf("行程Tab", "行程标签"), "trips", "行程")
    )

    /** 设置项名 → open_settings_section 的 target */
    private val SETTINGS_SECTIONS = listOf(
        Triple(listOf("回收站", "回收"), "trash", "回收站"),
        Triple(listOf("备份", "恢复", "快照"), "backup", "备份与恢复"),
        Triple(listOf("语言", "中英文"), "language", "语言"),
        Triple(listOf("主题", "外观", "深色", "浅色"), "theme", "外观"),
        Triple(listOf("触感", "震动", "振动"), "haptic", "触感反馈"),
        Triple(listOf("背景", "壁纸", "底图"), "background", "自定义背景"),
        Triple(listOf("小组件", "桌面卡片"), "widget", "桌面小组件"),
        Triple(listOf("个人资料", "头像", "昵称", "签名"), "profile", "个人资料"),
        Triple(listOf("日志", "崩溃"), "log", "崩溃日志"),
        Triple(listOf("隐私", "权限"), "privacy", "隐私政策"),
        Triple(listOf("安全", "加密"), "security", "安全中心"),
        Triple(listOf("本地模型", "本地大模型"), "local_model", "本地大模型"),
        Triple(listOf("语音库", "声音库", "音色"), "voice_library", "语音库"),
        Triple(listOf("API", "接口", "密钥"), "api_model", "API 模型"),
        Triple(listOf("模型分配", "分配模型"), "ai_routing", "AI 模型分配"),
        Triple(listOf("AI", "语音"), "ai", "AI 与语音"),
        Triple(listOf("通用", "缓存"), "general", "通用"),
        Triple(listOf("关于", "版本"), "about", "关于")
    )

    /** 开关名 → setting_switch 的 target */
    private val SWITCHES = listOf(
        Triple(listOf("消息通知", "通知"), "notifEnabled", "消息通知"),
        Triple(listOf("悬浮球"), "ai_ball", "AI 悬浮球"),
        Triple(listOf("语音输入", "语音录入"), "voice_input", "语音输入"),
        Triple(listOf("语音播报", "朗读", "念出来"), "voice_tts", "语音播报"),
        Triple(listOf("默认公开", "公开足迹", "公开"), "defaultPublic", "默认公开"),
        Triple(listOf("内容分享", "分享权限"), "shareEnabled", "内容分享")
    )

    fun parse(raw: String): CommandIntent? {
        val t = raw.trim().trim('。', '！', '!', '？', '?', '，', ',', '~', '.', '、')
        if (t.length < 2 || t.length > 30) return null
        // 问句一律交回模型：问「深色模式好不好看」「怎么关震动」不是要执行命令
        if (listOf("怎么", "如何", "为什么", "好不好", "是不是", "吗", "什么", "区别", "推荐").any { t.contains(it) }) return null
        // 「翻译成英文」是文本任务，不是切界面语言
        if (listOf("翻译", "润色", "回复用", "说英文", "讲英文").any { t.contains(it) }) return null

        theme(t)?.let { return it }
        haptic(t)?.let { return it }
        language(t)?.let { return it }
        settingsSection(t)?.let { return it }
        toggle(t)?.let { return it }
        openPage(t)?.let { return it }
        return null
    }

    private fun theme(t: String): CommandIntent? {
        if (listOf("主题", "外观", "模式", "皮肤").none { t.contains(it) } && !VERBS.any { t.contains(it) }) return null
        val dark = listOf("深色", "暗色", "夜间", "暗黑", "黑色").any { t.contains(it) }
        val light = listOf("浅色", "白天", "亮色", "白色").any { t.contains(it) }
        val follow = t.contains("跟随系统") || t.contains("跟系统")
        val target = when {
            follow -> "system"
            dark -> "dark"
            light -> "light"
            else -> return null
        }
        val label = when (target) {
            "dark" -> "切换到深色模式"
            "light" -> "切换到浅色模式"
            else -> "外观跟随系统"
        }
        return CommandIntent("theme", target, "", label)
    }

    private fun haptic(t: String): CommandIntent? {
        if (listOf("触感", "震动", "振动").none { t.contains(it) }) return null
        if (t.contains("怎么") || t.contains("如何") || t.contains("为什么") || t.contains("有没有")) return null
        // 注意「开关」里含「关」字：只认明确的关闭说法，否则会把「打开触感开关」判成关
        val off = listOf("关闭触感", "关掉触感", "关掉震动", "关掉振动", "关闭震动", "别震", "不要震",
            "取消触感", "取消震动").any { t.contains(it) }
        val on = listOf("开启触感", "打开触感", "开启震动", "打开震动", "开震动", "恢复触感",
            "震动开", "触感开").any { t.contains(it) }
        val follow = t.contains("跟随系统") || t.contains("跟系统")
        val target = when {
            follow -> "follow"
            off -> "off"
            on -> "on"
            else -> return null
        }
        val label = when (target) {
            "off" -> "关闭触感反馈"
            "on" -> "开启触感反馈"
            else -> "触感反馈跟随系统"
        }
        return CommandIntent("haptic", target, "", label)
    }

    private fun language(t: String): CommandIntent? {
        val hasLang = listOf("语言", "界面", "中文", "英文", "english", "chinese").any { t.contains(it, true) }
        if (!hasLang) return null
        if (VERBS.none { t.contains(it) }) return null
        val en = t.contains("英文", true) || t.contains("english", true)
        val zh = t.contains("中文", true) || t.contains("chinese", true)
        if (!en && !zh) return null
        val target = if (en) "en" else "zh"
        return CommandIntent("app_language", target, "", if (en) "把界面切成英文" else "把界面切回中文")
    }

    private fun settingsSection(t: String): CommandIntent? {
        if (t == "打开设置" || t == "去设置" || t == "进入设置" || t == "设置页") {
            return CommandIntent("settings_page", "main", "", "打开设置")
        }
        val m = Regex("(?:打开|进入|去)(?:一下)?设置(?:里的|中的|里面|里|中|的)?(.{1,10})").find(t) ?: return null
        val tail = m.groupValues[1].trim()
        if (tail.isBlank()) return CommandIntent("settings_page", "main", "", "打开设置")
        val hit = SETTINGS_SECTIONS.firstOrNull { it.first.any { k -> tail.contains(k, true) } } ?: return null
        return CommandIntent("settings_page", hit.second, "", "打开设置：${hit.third}")
    }

    private fun toggle(t: String): CommandIntent? {
        val hit = SWITCHES.firstOrNull { it.first.any { k -> t.contains(k, true) } } ?: return null
        // 只认「开/关」语义，问句（「通知怎么关」）交给模型
        val off = listOf("关闭", "关掉", "关了", "别", "取消", "不开").any { t.contains(it) }
        val on = listOf("开启", "打开", "开一下", "恢复").any { t.contains(it) }
        if (!off && !on) return null
        if (t.contains("怎么") || t.contains("如何") || t.contains("为什么")) return null
        val value = if (off) "false" else "true"
        return CommandIntent("setting", hit.second, value, "${if (off) "关闭" else "开启"}${hit.third}")
    }

    private fun openPage(t: String): CommandIntent? {
        if (OPEN_VERBS.none { t.contains(it) }) return null
        val hit = PAGES.firstOrNull { it.first.any { k -> t.contains(k, true) } } ?: return null
        // 只是「打开设置」这种由 settings_page 处理，这里不接
        return CommandIntent("open", hit.second, "", "打开${hit.third}")
    }
}
