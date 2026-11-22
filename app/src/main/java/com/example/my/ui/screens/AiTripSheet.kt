package com.example.my.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.AiApi
import com.example.my.data.AiFeature
import com.example.my.data.AiTripBlueprint
import com.example.my.data.TripResearch
import com.example.my.data.WeatherApi
import com.example.my.data.WebHit
import com.example.my.data.WebSearchPrefs
import com.example.my.data.ai.LocalLlm
import com.example.my.state.AppState
import com.example.my.ui.interaction.WeekDateStrip
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Func
import com.example.my.ui.theme.MainGradient
import com.example.my.ui.theme.Text
import com.example.my.ui.i18n.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val aiBudgets = listOf("经济", "舒适", "轻奢")
private val aiPreferences = listOf("自然风光", "人文历史", "美食", "亲子", "慢节奏")
private val aiDayChoices = listOf(3, 5, 7)
private val aiDateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
private const val LOCAL_MODEL_LABEL = "本地模型"
/** 参考网页折叠列表默认展示条数，其余点「展开全部」 */
private const val TRIP_SOURCE_VISIBLE = 4

/**
 * AI 生成行程：自定义天数/预算/模型，点生成后按钮收起，在原区域流式输出，
 * 思考部分折叠，完成后可一键保存为出行计划。
 */
@Composable
fun AiTripSheet(state: AppState, onDismiss: () -> Unit) {
    var destination by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(5) }
    var customDays by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("舒适") }
    var customBudget by remember { mutableStateOf("") }
    var preferences by remember { mutableStateOf(setOf("自然风光")) }
    // 「自动」= 使用设置页给「AI 行程规划」分配的端点（默认跟随全局）
    var modelChoice by remember { mutableStateOf("自动") }
    var startDate by remember { mutableStateOf("") }

    // 动态模型列表：自动 + 所有已接入对话端点 + 本地模型
    val modelOptions = remember(state.chatEndpoints) {
        buildList {
            add("自动")
            state.chatEndpoints.forEach { ep -> add(ep.name) }
            add(LOCAL_MODEL_LABEL)
        }
    }

    var streaming by remember { mutableStateOf(false) }
    var rawOutput by remember { mutableStateOf("") }
    var thinkingText by remember { mutableStateOf("") }
    var bodyText by remember { mutableStateOf("") }
    var showThinking by remember { mutableStateOf(false) }
    var blueprint by remember { mutableStateOf<AiTripBlueprint?>(null) }
    var destWeatherInfo by remember { mutableStateOf("") }
    /** 本轮生成实际投喂给模型的网页攻略，生成结束后给用户一份可展开、可点开的清单 */
    var webSources by remember { mutableStateOf<List<WebHit>>(emptyList()) }

    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // 与 AI 助手对话共用的联网开关（SP 持久化）：开着才检索网页攻略，
    // 关掉就是纯本地规划，绝不偷偷联网
    var webEnabled by remember { mutableStateOf(WebSearchPrefs.isEnabled(ctx)) }
    /** 检索阶段提示：生成前会先联网找攻略，用户要能看见这一步 */
    var webPhase by remember { mutableStateOf("") }

    // 目的地变化时自动查天气
    LaunchedEffect(destination) {
        val city = destination.trim()
        if (city.length < 2) { destWeatherInfo = ""; return@LaunchedEffect }
        try {
            val hits = WeatherApi.searchCity(city)
            val hit = hits.firstOrNull() ?: return@LaunchedEffect
            val w = WeatherApi.fetch(hit.name, hit.lat, hit.lon)
            val dayStr = w.days.take(5).joinToString("；") { d ->
                "${d.day} ${d.low}~${d.high}°C ${d.desc}"
            }
            destWeatherInfo = "目的地天气实况：${w.temp}°C ${w.desc}，湿度${w.humidity}%\n未来5天：$dayStr"
        } catch (_: Exception) {
            destWeatherInfo = ""
        }
    }

    fun effectiveDays(): Int = customDays.toIntOrNull()?.takeIf { it in 1..30 } ?: days
    fun effectiveBudget(): String = customBudget.trim().ifBlank { budget }

    /** 综合日期/天气/节假日/旺季生成出行参考文本 */
    fun buildTravelContext(): String {
        val parts = mutableListOf<String>()
        if (destWeatherInfo.isNotBlank()) parts.add(destWeatherInfo)
        val cal = java.util.Calendar.getInstance()
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)

        val tags = mutableListOf<String>()
        if (dow == 1 || dow == 7) tags.add("当前是周末")
        val holiday = when {
            (month == 1 && day in 1..5) || (month == 2 && day in 1..15) -> "春节/寒假黄金周（出行高峰，机票酒店贵、景点人多）"
            month == 5 && day in 1..5 -> "五一黄金周（出行高峰，景区拥挤）"
            month == 10 && day in 1..7 -> "国庆黄金周（全年最高峰，务必提前预约）"
            month == 4 && day in 4..6 -> "清明小长假"
            month == 6 && day in 20..25 -> "端午小长假"
            month == 9 && day in 15..25 -> "中秋小长假"
            month == 1 && day in 1..3 -> "元旦小长假"
            else -> null
        }
        if (holiday != null) tags.add(holiday)
        val peakSeason = when {
            month in 7..8 -> "暑假旺季（学生流，亲子游多，热门景点需排队）"
            month == 12 || month in 1..2 -> "冬季（南方如三亚/昆明旺季，北方如哈尔滨冰雪季旺季）"
            month in 3..5 -> "春季（赏花季，云南/江南适中，气候宜人）"
            month in 9..10 -> "秋季（赏秋季，九寨沟/北疆旺季，人流适中）"
            else -> ""
        }
        if (peakSeason.isNotBlank()) tags.add(peakSeason)
        if (tags.none { it.contains("周末") || it.contains("黄金周") || it.contains("小长假") }) {
            tags.add("当前是工作日，景点人少、酒店便宜，适合错峰出行")
        }
        parts.add("出行时机：${tags.joinToString("；")}")
        parts.add("用户计划 ${effectiveDays()} 天行程，请合理分配每日强度，避免赶场。")
        return parts.joinToString("\n")
    }

    fun startGenerate() {
        if (destination.isBlank() || streaming) return
        streaming = true
        rawOutput = ""
        thinkingText = ""
        bodyText = ""
        blueprint = null
        startDate = ""
        webPhase = ""
        webSources = emptyList()

        scope.launch {
            try {
                // 先把模型落点定下来：检索清洗/事实裁决需要知道是云端还是端侧，
                // 端侧模型只喂压缩后的短材料，云端模型可以多给一些来源摘要。
                // 「自动」时跟随设置页给「AI 行程规划」的分配；
                // 分配给本机模型时也走端侧，否则仍按端点走云端。
                val useLocal = modelChoice == LOCAL_MODEL_LABEL ||
                    (modelChoice == "自动" && state.aiFeatureUsesLocal(AiFeature.TRIP))
                // 「本地模型」这个芯片只表示本机、不点名具体包，沿用当前启用模型；
                // 「自动」跟随分配页里给行程规划指定的具体本地模型。
                val localModelId = if (modelChoice == LOCAL_MODEL_LABEL) null
                else state.aiFeatureLocalModelId(AiFeature.TRIP)
                // 根据选中名字找对应端点（自动/未指定时用当前端点）
                val targetEp = when {
                    useLocal -> null
                    modelChoice == "自动" -> state.aiFeatureEndpoint(AiFeature.TRIP)
                    else -> state.chatEndpoints.firstOrNull { it.name == modelChoice } ?: state.aiFeatureEndpoint(AiFeature.TRIP)
                }

                // 联网开着就先做多源攻略检索：官方文旅/OTA/知乎/小红书/路线/交通，
                // 再清洗、按来源层级裁决，最后压缩成事实卡。不是把 6 篇网页原文直接塞给模型。
                val webMaterial = if (webEnabled) {
                    webPhase = tr("正在检索官方/OTA/游记多源攻略…", "Searching official, OTA and travel guides…")
                    val research = TripResearch.research(
                        destination = destination.trim(),
                        days = effectiveDays(),
                        budget = effectiveBudget(),
                        preference = if (preferences.isEmpty()) "无特别偏好" else preferences.joinToString("、"),
                        ep = targetEp,
                        isLocal = useLocal,
                        localModel = localModelId.orEmpty(),
                        context = ctx
                    )
                    webSources = research.sources
                    research.material
                } else ""
                webPhase = ""
                // 出行参考：天气 + 节假日/旺季 + 本轮联网查到的压缩证据
                val extraContext = buildTravelContext() +
                    if (webMaterial.isNotBlank()) {
                        "\n\n【联网攻略资料·生成行程必须参考】\n$webMaterial"
                    } else ""
                if (useLocal) {
                    // 本地模型一次性返回，模拟流式
                    val prompt = "目的地：${destination.trim()}，${effectiveDays()}天，预算${effectiveBudget()}，偏好${preferences.joinToString("、")}。" +
                        "参考（必须优先采用多源检索压缩证据里真实出现的景点名、官方门票/预约与交通提示；" +
                        "UGC 只用于路线与避坑，没写的价格、营业时间、车次一律不要编）：$extraContext。" +
                        "输出逐日行程 JSON：title 是概括性标题（地点＋玩法/气质，8-16 字，不要「X日游」）；" +
                        "summary 2-3 句概括骨架与气质，并点出最值得站的一处；" +
                        "bestSpot 给 place（具体点位）/moment（最佳时段或光线）/reason（为什么最好）；" +
                        "每天 3-4 条 items 按时间顺序，每条都要带「人」（渔民/摊主/手艺人/当地老人）" +
                        "或「环境」（风/光线/水位/气味/街巷尺度）的具体细节，不要只报地名；" +
                        "每天另给 bestSpot（位置＋时段＋为什么）。" +
                        "schema：{\"title\":\"\",\"destination\":\"\",\"summary\":\"\",\"bestSpot\":{\"place\":\"\",\"moment\":\"\",\"reason\":\"\"}," +
                        "\"days\":[{\"day\":1,\"title\":\"\",\"bestSpot\":\"\",\"items\":[]}],\"packing\":[],\"tips\":[],\"advice\":[]}"
                    val resp = LocalLlm.chatOnce(ctx, prompt, localModelId) ?: ""
                    // 按 \u0001 前缀拆分思考/正文
                    val segs = resp.split("\u0001", limit = 3)
                    thinkingText = when {
                        segs.size >= 3 -> segs[1].trim()
                        segs.size == 2 && segs[0].isBlank() -> segs[1].trim()
                        else -> ""
                    }
                    val body = when {
                        segs.size >= 3 -> segs[2]
                        segs.size == 2 && segs[0].isBlank() -> ""
                        else -> resp
                    }
                    // 30fps 打字机：原来每字符 15ms 且 acc += ch 是 O(n²) 字符串拼接，
                    // 长回答会一直重建整段文本并占满主线程
                    var shown = 0
                    while (shown < body.length) {
                        shown = minOf(body.length, shown + 2)
                        bodyText = body.substring(0, shown)
                        delay(30)
                    }
                    rawOutput = resp
                } else if (targetEp != null) {
                    AiApi.generateTripPlanStream(
                        ep = targetEp,
                        destination = destination.trim(),
                        days = effectiveDays(),
                        budget = effectiveBudget(),
                        preference = if (preferences.isEmpty()) "无特别偏好" else preferences.joinToString("、"),
                        extraContext = extraContext
                    ).collect { chunk ->
                        rawOutput += chunk
                        // sseFlow 已把思考内容以 \u0001 前缀 emit，正文直接 emit
                        if (chunk.startsWith("\u0001")) {
                            thinkingText += chunk.removePrefix("\u0001")
                        } else {
                            bodyText += chunk
                        }
                    }
                }
                // 收完后用纯正文（已剥离 \u0001 思考）尝试解析 blueprint
                val parsed = AiApi.parseTripBlueprint(bodyText.ifBlank { rawOutput })
                blueprint = parsed
            } catch (e: Exception) {
                bodyText = (if (bodyText.isNotEmpty()) bodyText + "\n\n" else "") + "生成失败：${e.message ?: "网络异常"}"
            } finally {
                streaming = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Func.Scrim)
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Bg.Surface)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Bg.Outline)
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("AI 生成行程"),
                    color = Text.Primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Bg.SurfaceVariant)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, tr("关闭"), tint = Text.Secondary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                tr("输入想法，让 AI 排好每天的路线，确认后即可保存为自己的行程"),
                color = Text.Muted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(18.dp))

            // 目的地
            PlanField(label = tr("目的地"), required = true) {
                PlanInput(destination, tr("例如：大理、成都、京都")) { destination = it }
            }
            Spacer(Modifier.height(14.dp))

            // 天数：预设 + 自定义
            PlanField(label = tr("天数")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    aiDayChoices.forEach { n ->
                        val selected = customDays.isEmpty() && days == n
                        AiChip(tr("$n 天", "$n days"), selected = selected) {
                            days = n
                            customDays = ""
                        }
                    }
                    // 自定义输入
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(if (customDays.isNotBlank()) Brand.Terracotta.copy(alpha = 0.10f) else Bg.SurfaceVariant)
                            .border(1.dp, if (customDays.isNotBlank()) Brand.Terracotta.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(50.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = customDays,
                            onValueChange = { customDays = it.filter { c -> c.isDigit() }.take(2) },
                            singleLine = true,
                            textStyle = TextStyle(color = Brand.Terracotta, fontSize = 12.sp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            decorationBox = { inner ->
                                if (customDays.isEmpty()) {
                                    Text(tr("自定义天数"), color = Text.Secondary, fontSize = 12.sp)
                                }
                                inner()
                            }
                        )
                        if (customDays.isNotEmpty()) {
                            Text("天", color = Brand.Terracotta, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            // 预算：预设 + 自定义
            PlanField(label = tr("预算")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    aiBudgets.forEach { b ->
                        val selected = customBudget.isBlank() && budget == b
                        AiChip(tr(b), selected = selected) {
                            budget = b
                            customBudget = ""
                        }
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(if (customBudget.isNotBlank()) Brand.Terracotta.copy(alpha = 0.10f) else Bg.SurfaceVariant)
                            .border(1.dp, if (customBudget.isNotBlank()) Brand.Terracotta.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(50.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = customBudget,
                            onValueChange = { customBudget = it.take(10) },
                            singleLine = true,
                            textStyle = TextStyle(color = Brand.Terracotta, fontSize = 12.sp),
                            decorationBox = { inner ->
                                if (customBudget.isEmpty()) {
                                    Text(tr("自定义预算"), color = Text.Secondary, fontSize = 12.sp)
                                }
                                inner()
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            // 偏好
            PlanField(label = tr("偏好")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    aiPreferences.take(3).forEach { p ->
                        AiChip(tr(p), selected = p in preferences) {
                            preferences = if (p in preferences) preferences - p else preferences + p
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    aiPreferences.drop(3).forEach { p ->
                        AiChip(tr(p), selected = p in preferences) {
                            preferences = if (p in preferences) preferences - p else preferences + p
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            // 模型选择：自动 + 所有已接入端点 + 本地模型
            PlanField(label = tr("AI 模型")) {
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(modelOptions) { m ->
                        AiChip(tr(m), selected = modelChoice == m) { modelChoice = m }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            // 联网检索开关：与 AI 助手对话共用同一份设置，开着就先查当地真实攻略
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Bg.SurfaceVariant)
                    .clickable {
                        webEnabled = !webEnabled
                        WebSearchPrefs.setEnabled(ctx, webEnabled)
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (webEnabled) Brand.Terracotta else Text.Muted.copy(alpha = 0.5f))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (webEnabled) tr("联网检索攻略 · 已开启", "Web guides · ON")
                    else tr("联网检索攻略 · 已关闭", "Web guides · OFF"),
                    color = if (webEnabled) Brand.Terracotta else Text.Muted,
                    fontSize = 12.sp,
                    fontWeight = if (webEnabled) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    tr("知乎 / 小红书 / 抖音", "Zhihu / Xiaohongshu / Douyin"),
                    color = Text.Muted,
                    fontSize = 10.sp
                )
            }
            Spacer(Modifier.height(14.dp))

            // 生成按钮：点了就收起，在下面流式输出
            if (!streaming && blueprint == null && rawOutput.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50.dp))
                        .background(Brush.linearGradient(MainGradient))
                        .clickable(enabled = destination.isNotBlank()) { startGenerate() }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(tr("AI 生成行程"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // 流式输出区
            if (streaming || rawOutput.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Bg.SurfaceVariant)
                        .padding(12.dp)
                ) {
                    Column {
                        if (streaming) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = Brand.Terracotta,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (webPhase.isNotBlank()) webPhase else tr("AI 正在规划…", "Planning…"),
                                    color = Brand.Terracotta,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        // 思考折叠
                        if (thinkingText.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Bg.Surface)
                                    .clickable { showThinking = !showThinking }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (showThinking) "▾ ${tr("思考过程")}" else "▸ ${tr("思考过程")}（${thinkingText.length} 字）",
                                    color = Text.Muted, fontSize = 11.sp
                                )
                            }
                            if (showThinking) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    thinkingText,
                                    color = Text.Muted, fontSize = 11.sp, lineHeight = 16.sp
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        // 正文流式
                        if (bodyText.isNotBlank()) {
                            Text(
                                stripControlMarkers(bodyText),
                                color = Text.Primary, fontSize = 12.sp, lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // 没有解析出结构化行程时，也把参考网页挂出来，避免「联网了却看不到证据」
            if (blueprint == null && !streaming && webSources.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                TripSourcesCard(
                    sources = webSources,
                    onOpen = { url -> state.openUrl(url) }
                )
            }

            // blueprint 解析成功后：结构化展示 + 一键保存
            blueprint?.let { bp ->
                Spacer(Modifier.height(16.dp))
                Text(bp.title, color = Text.Primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (bp.summary.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(bp.summary, color = Text.Secondary, fontSize = 12.sp, lineHeight = 18.sp)
                }
                // 全程最值得站的一处：位置、时段、理由，单独一块，不埋在摘要里
                bp.bestSpot?.takeIf { !it.isBlank }?.let { spot ->
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brand.Teal.copy(alpha = 0.10f))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Place,
                                contentDescription = null,
                                tint = Brand.Teal,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                tr("全程最值得站的一处"),
                                color = Brand.Teal, fontSize = 12.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        val head = listOf(spot.place, spot.moment)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                        if (head.isNotBlank()) {
                            Spacer(Modifier.height(5.dp))
                            Text(
                                head, color = Text.Primary, fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold, lineHeight = 19.sp
                            )
                        }
                        if (spot.reason.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(spot.reason, color = Text.Secondary, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                    }
                }
                // 行程标题/摘要之后先给出来源入口：默认折叠，不打断阅读，
                // 用户想核对 AI 到底参考了哪些游记再展开。
                if (!streaming && webSources.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    TripSourcesCard(
                        sources = webSources,
                        onOpen = { url -> state.openUrl(url) }
                    )
                }
                Spacer(Modifier.height(14.dp))

                bp.days.forEach { d ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Bg.SurfaceVariant)
                            .padding(12.dp)
                    ) {
                        Text(
                            "D${d.day}｜${d.title}",
                            color = Text.Primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        d.items.forEach { item ->
                            Text("· $item", color = Text.Secondary, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                        // 当天该站在哪儿：位置＋时段＋为什么，跟具体地名一起看才用得上
                        if (d.bestSpot.isNotBlank()) {
                            Spacer(Modifier.height(5.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Place,
                                    contentDescription = null,
                                    tint = Brand.Teal,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(d.bestSpot, color = Brand.Teal, fontSize = 11.sp, lineHeight = 16.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // 出行建议（综合日期/天气/高峰期）
                if (bp.advice.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brand.Terracotta.copy(alpha = 0.08f))
                            .padding(12.dp)
                    ) {
                        Text(
                            tr("出行建议"),
                            color = Brand.Terracotta, fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        bp.advice.forEach { a ->
                            Text("· $a", color = Text.Primary, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                    }
                }

                if (bp.packing.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(tr("建议携带"), color = Text.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(bp.packing.joinToString(" · "), color = Text.Muted, fontSize = 12.sp, lineHeight = 18.sp)
                }
                if (bp.tips.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(tr("当地提示"), color = Text.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    bp.tips.forEach { tip ->
                        Text("· $tip", color = Text.Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(tr("选择出发日期后保存"), color = Text.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                // 简单可靠的横向日期列表：未来14天可选
                val todayMillis = remember {
                    java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }
                val dateList = remember(todayMillis) {
                    (0 until 14).map { offset ->
                        java.util.Calendar.getInstance().apply {
                            timeInMillis = todayMillis
                            add(java.util.Calendar.DAY_OF_YEAR, offset)
                        }.timeInMillis
                    }
                }
                val dowNames = listOf("日", "一", "二", "三", "四", "五", "六")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(dateList) { millis ->
                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
                        val isSelected = startDate.isNotBlank() &&
                            runCatching { aiDateFmt.parse(startDate)?.time }.getOrNull() == millis
                        Column(
                            modifier = Modifier
                                .width(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) Brand.Terracotta else Bg.SurfaceVariant)
                                .clickable {
                                    startDate = aiDateFmt.format(Date(millis))
                                }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                dowNames[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1],
                                color = if (isSelected) Color.White else Text.Muted,
                                fontSize = 10.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${cal.get(java.util.Calendar.DAY_OF_MONTH)}",
                                color = if (isSelected) Color.White else Text.Primary,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                val canSave = startDate.isNotBlank()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50.dp))
                        .background(
                            if (canSave) Brush.linearGradient(MainGradient)
                            else Brush.linearGradient(listOf(Text.Muted.copy(alpha = 0.35f), Text.Muted.copy(alpha = 0.35f)))
                        )
                        .clickable(enabled = canSave) {
                            state.addTripPlan(
                                name = bp.title.ifBlank { tr("${bp.destination} ${bp.days.size} 日", "${bp.destination} · ${bp.days.size} days") },
                                destination = bp.destination,
                                startDate = startDate,
                                days = bp.days.size,
                                note = bp.summary,
                                dailyPlan = bp.days.map { d ->
                                    "D${d.day}｜${d.title}：" + d.items.joinToString(" / ") +
                                        if (d.bestSpot.isNotBlank()) "（最佳位置：${d.bestSpot}）" else ""
                                },
                                customPacking = bp.packing
                            )
                            state.toast(tr("行程已保存"))
                            onDismiss()
                        }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (canSave) tr("一键出行计划") else tr("请选择出发日期"),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 重新生成按钮（生成完后显示）
            if (blueprint != null && !streaming) {
                Spacer(Modifier.height(10.dp))
                Text(
                    tr("重新生成"),
                    color = Brand.Terracotta, fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { startGenerate() }
                        .padding(8.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 参考网页折叠卡：默认收起，展开后逐条可点，点开在 App 内浏览器查看 */
@Composable
private fun TripSourcesCard(sources: List<WebHit>, onOpen: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    val visible = if (showAll) sources.size else minOf(TRIP_SOURCE_VISIBLE, sources.size)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Bg.Surface)
            .border(1.dp, Brand.Terracotta.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Language,
                contentDescription = null,
                tint = Brand.Terracotta,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tr("参考网页 · ${sources.size} 篇", "Sources · ${sources.size} pages"),
                    color = Brand.Terracotta,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    tr("本次行程实际用到的联网攻略，可选择性查看", "Web guides used for this trip"),
                    color = Text.Muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                if (expanded) tr("收起", "Hide") else tr("展开", "Show"),
                color = Brand.Terracotta,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (expanded) {
            Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp)) {
                sources.take(visible).forEachIndexed { i, h ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = h.url.startsWith("http")) { onOpen(h.url) }
                            .padding(horizontal = 6.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("[${i + 1}]", color = Brand.Terracotta, fontSize = 10.sp)
                        Spacer(Modifier.width(7.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                h.title.ifBlank { h.url },
                                color = Text.Primary,
                                fontSize = 11.5.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (h.site.isNotBlank()) {
                                Text(
                                    h.site,
                                    color = Text.Muted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(tr("查看", "Open"), color = Text.Muted, fontSize = 10.sp)
                    }
                }
                if (sources.size > TRIP_SOURCE_VISIBLE) {
                    Text(
                        if (showAll) tr("只看前 ${TRIP_SOURCE_VISIBLE} 篇", "Show top ${TRIP_SOURCE_VISIBLE}")
                        else tr("展开全部 ${sources.size} 篇", "Show all ${sources.size}"),
                        color = Brand.Terracotta,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clickable { showAll = !showAll }
                            .padding(horizontal = 8.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

/** 胶囊选择项 */
@Composable
private fun AiChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Brand.Terracotta else Text.Secondary,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) Brand.Terracotta.copy(alpha = 0.10f) else Bg.SurfaceVariant)
            .border(
                width = 1.dp,
                color = if (selected) Brand.Terracotta.copy(alpha = 0.35f) else Color.Transparent,
                shape = RoundedCornerShape(50.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
