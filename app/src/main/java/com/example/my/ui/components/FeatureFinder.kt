package com.example.my.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.my.state.AppState
import com.example.my.state.ExtraScreen
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Layer
import com.example.my.ui.theme.Radius
import com.example.my.ui.theme.Spacing
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.cardShadow

/**
 * 一条可被搜索的功能入口。
 *
 * [action] 里放的是「点了之后真的跳过去」的动作，所以搜索框本身不需要知道各页面的结构；
 * 设置页和侧屏各自把本地的目标拼进列表，全局功能走 [appFeatureItems] 共用一份。
 */
data class FeatureItem(
    val key: String,
    val groupZh: String,
    val groupEn: String,
    val titleZh: String,
    val titleEn: String,
    val hintZh: String = "",
    val hintEn: String = "",
    /** 额外检索词：别名、拼音、常见口语说法 */
    val keywords: String = "",
    val action: () -> Unit
) {
    internal fun haystack(): String =
        "$titleZh|$titleEn|$groupZh|$groupEn|$hintZh|$hintEn|$keywords".lowercase()
}

/** 全 App 范围的功能表：设置页与侧屏共用，保证两边搜到的东西一致 */
fun appFeatureItems(state: AppState): List<FeatureItem> = listOf(
    FeatureItem(
        "upload", "足迹", "Footprints", "新建足迹", "New footprint",
        "拍照 · 视频 · 实况 · AI 整理", "Photos · video · AI fill",
        "上传 记录 写 打卡 upload footprint record"
    ) { state.openUpload() },
    FeatureItem(
        "footprints", "足迹", "Footprints", "我的足迹", "My footprints",
        "地图上的全部打卡点", "All pins on the map", "地图 打卡 城市"
    ) {
        state.activeTab = com.example.my.state.AppTab.MAP
        state.footprintMode = 0
    },
    FeatureItem(
        "trips", "行程", "Trips", "我的行程", "My trips",
        "计划中的旅程", "Planned journeys", "计划 旅程 路线"
    ) { state.openScreen(ExtraScreen.MY_PLANS) },
    FeatureItem(
        "year_report", "足迹", "Footprints", "年度报告", "Year report",
        "今年走过多少城", "Cities this year", "统计 年度 报告 总结"
    ) { state.openScreen(ExtraScreen.YEAR_REPORT) },
    FeatureItem(
        "notifications", "消息", "Messages", "通知消息", "Notifications",
        "点赞 · 关注 · 评论", "Likes · follows · comments", "提醒 未读 红点"
    ) { state.openScreen(ExtraScreen.NOTIFICATIONS) },
    FeatureItem(
        "messages", "消息", "Messages", "私信", "Direct messages",
        "会话 · 互关 · 聊天", "Chats · mutual follow", "消息 私信 聊天 关注 互关"
    ) { state.openScreen(ExtraScreen.MESSAGES) },
    FeatureItem(
        "ai", "AI", "AI", "AI 助手", "AI assistant",
        "对话 · 生成 · 规划", "Chat · generate · plan", "智能 语音 大模型 豆包"
    ) { state.openAiFull() },
    FeatureItem(
        "guide_create", "攻略", "Guides", "发布攻略", "Publish a guide",
        "把旅程写成攻略", "Turn a trip into a guide", "写攻略 发布 创作"
    ) { state.openScreen(ExtraScreen.GUIDE_CREATE) },
    FeatureItem(
        "offline", "地图", "Map", "离线地图包", "Offline maps",
        "提前缓存要去的城市", "Cache cities before a trip", "地图包 下载 离线 缓存区域"
    ) {
        state.activeTab = com.example.my.state.AppTab.MAP
        state.requestMapPacks()
    },
    FeatureItem(
        "weather", "发现", "Discover", "天气", "Weather",
        "当前与未来天气", "Now and upcoming", "温度 预报 下雨"
    ) { state.openScreen(ExtraScreen.WEATHER) },
    FeatureItem(
        "news", "发现", "Discover", "资讯", "News",
        "热点与攻略原文", "Hot takes and sources", "新闻 热点 头条"
    ) { state.openScreen(ExtraScreen.NEWS) },
    FeatureItem(
        "stock", "发现", "Discover", "股市行情", "Markets",
        "A股 · 港股 · 美股指数", "A / HK / US indices", "股票 基金 大盘 指数 涨跌 行情 美股 A股 港股"
    ) { state.openScreen(ExtraScreen.STOCK) },
    FeatureItem(
        "trade", "工具", "Tools", "我的交易", "My trading",
        "模拟盘：资产 · 委托 · 持仓 · 自选", "Paper trading: assets, orders, holdings",
        "交易 模拟盘 持仓 委托 买入 卖出 自选 资产 定投 资金"
    ) { state.openScreen(ExtraScreen.TRADE) },
    FeatureItem(
        "music", "发现", "Discover", "音乐", "Music",
        "正在播放与歌单", "Now playing and playlists", "播放器 歌 歌单 电台 歌词"
    ) { state.openScreen(ExtraScreen.MUSIC) },
    FeatureItem(
        "course", "工具", "Tools", "课程表", "Timetable",
        "本周课程安排", "This week's lessons", "课程 上课 表 时间表"
    ) { state.openScreen(ExtraScreen.COURSE) },
    FeatureItem(
        "travelers", "发现", "Discover", "常访问的旅行人", "Frequent travelers",
        "同行人与攻略作者", "Companions and authors", "旅行人 排行 作者 同行"
    ) { state.openScreen(ExtraScreen.TRAVELER_RANK) },
    FeatureItem(
        "creative", "工具", "Tools", "创意工坊", "Creative studio",
        "图片与卡片生成", "Images and cards", "创作 生成 图片 卡片 海报"
    ) { state.openScreen(ExtraScreen.CREATIVE) },
    FeatureItem(
        "tickets", "工具", "Tools", "票务专区", "Tickets",
        "门票 · 机票 · 酒店", "Tickets · flights · hotels", "买票 机票 酒店 门票 预订"
    ) { state.openScreen(ExtraScreen.TICKET_ZONE) },
    FeatureItem(
        "shopping", "工具", "Tools", "购物专区", "Shopping",
        "任意商品比价与价格走势", "Compare prices and view trends",
        "购物 买东西 比价 价格 走势 报价 京东 淘宝 便宜"
    ) { state.openScreen(ExtraScreen.SHOPPING) },
    FeatureItem(
        "privacy", "工具", "Tools", "隐私空间", "Private space",
        "加密相册 · 批量导入与移出", "Encrypted gallery · batch import",
        "隐私 加密 相册 照片 视频 密码 隐藏 vault privacy gallery"
    ) { state.openScreen(ExtraScreen.PRIVACY_VAULT) },
    FeatureItem(
        "web_search", "发现", "Discover", "网页搜索", "Web search",
        "查攻略原文与出处", "Look up sources", "搜索 网页 原文 出处"
    ) { state.openScreen(ExtraScreen.WEB_SEARCH) },
    FeatureItem(
        "driving", "出行", "Travel", "驾驶模式", "Driving mode",
        "全屏车机 · 导航 / 音乐 / 语音", "Full-screen cockpit · nav / music / voice",
        "开车 车机 车载 导航 驾驶 car drive cockpit"
    ) { state.openScreen(ExtraScreen.DRIVING) },
    FeatureItem(
        "settings", "系统", "System", "设置", "Settings",
        "外观 · 语言 · 隐私", "Theme · language · privacy", "偏好 配置 隐私 主题"
    ) { state.openScreen(ExtraScreen.SETTINGS) },
    FeatureItem(
        "switch_account", "系统", "System", "切换账号", "Switch account",
        "退出后换个身份登录", "Sign in as another", "账号 退出 登录 换号"
    ) { state.switchAccount() }
)

/** 按空格切词、逐词全命中：输入「设置 语言」也能命中 */
fun filterFeatures(items: List<FeatureItem>, query: String): List<FeatureItem> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return items
    val terms = q.split(' ', '\u3000').filter { it.isNotBlank() }
    return items.filter { item ->
        val hay = item.haystack()
        terms.all { hay.contains(it) }
    }
}

/**
 * 收纳态的一颗「搜索功能」胶囊。
 *
 * 收起时只占一行；点开浮层，输入法自动弹出，边输边筛，点结果直接跳过去并收起。
 * 设置页与侧屏都挂这一个组件，行为完全一致。
 */
@Composable
fun FeatureSearchPill(
    items: List<FeatureItem>,
    modifier: Modifier = Modifier,
    onBeforeJump: (() -> Unit)? = null
) {
    var open by remember { mutableStateOf(false) }

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Bg.SurfaceVariant.copy(alpha = 1f))
            .clickable { open = true }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Search, null, tint = Text.Muted, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            tr("搜索功能（足迹 · 行情 · 设置…）", "Search features (footprints · markets · settings…)"),
            fontSize = 12.5.sp,
            color = Text.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    if (open) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        Dialog(
            onDismissRequest = {
                shown = false
                open = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = shown,
                    enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { -it / 6 },
                    exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { -it / 6 }
                ) {
                    FeatureSearchPanel(
                        items = items,
                        onClose = {
                            shown = false
                            open = false
                        },
                        onPick = { item ->
                            onBeforeJump?.invoke()
                            shown = false
                            open = false
                            item.action()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureSearchPanel(
    items: List<FeatureItem>,
    onClose: () -> Unit,
    onPick: (FeatureItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        // 浮层刚进入组合时输入框可能还没挂到窗口上，requestFocus 会抛异常
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }

    val results = remember(query, items) { filterFeatures(items, query) }

    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .imePadding()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .cardShadow(RoundedCornerShape(Radius.lg))
                .clip(RoundedCornerShape(Radius.lg))
                .background(Layer.Opaque)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // 输入行：放大镜 + 输入框 + 关闭
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, null, tint = Text.Muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            tr("搜索功能，例如「行情」「回收站」", "Search features, e.g. markets, trash"),
                            fontSize = 14.sp, color = Text.Muted
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Text.Primary, fontSize = 14.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Brand.Terracotta),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Outlined.Close, tr("清空", "Clear"), tint = Text.Muted,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { query = "" }
                    )
                } else {
                    Text(
                        tr("关闭", "Close"),
                        fontSize = 12.sp, color = Text.Muted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                keyboard?.hide()
                                onClose()
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(0.6.dp).background(Bg.Outline.copy(alpha = 0.25f)))
            Spacer(Modifier.height(6.dp))

            if (results.isEmpty()) {
                Text(
                    tr("没有找到这个功能，换个说法试试", "Nothing matches — try another word"),
                    fontSize = 12.sp, color = Text.Muted,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(results, key = { it.key }) { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.sm))
                                .clickable { onPick(item) }
                                .padding(vertical = 10.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    tr(item.titleZh, item.titleEn),
                                    fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                    color = Text.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                if (item.hintZh.isNotBlank() || item.hintEn.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        tr(item.hintZh, item.hintEn),
                                        fontSize = 11.sp, color = Text.Muted,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                tr(item.groupZh, item.groupEn),
                                fontSize = 11.sp, color = Text.Muted
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                                tint = Text.Muted, modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
