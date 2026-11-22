package com.example.my.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.my.data.map.DrivePref
import com.example.my.data.map.DriveRoute
import com.example.my.data.map.LatLon
import com.example.my.data.music.LocalSong
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.DrivingPalette
import com.example.my.ui.theme.Ink
import com.example.my.ui.theme.TypeScale
import java.util.Locale

/* =====================================================================
 *  巡航 / 搜索态：一条地址候选
 * ===================================================================== */

/** 一条地址候选：标题 / 详细地址 / 距离 / 坐标（选中即算路，必须带点）。 */
internal data class DrivePlaceHit(
    val title: String,
    val addr: String,
    val dist: String,
    val point: LatLon?
)

/**
 * 搜索面板。
 *
 * 这里是一个**真的输入框**（BasicTextField），不是一张画着光标的假卡片：
 * 车机上可以直接打字，也可以走系统输入法的语音键。结果全部来自
 * 高德 POI 检索，选中后带着真实坐标进入路线预览。
 */
@Composable
internal fun DriveSearchPanel(
    nx: (Number) -> Dp,
    ny: (Number) -> Dp,
    query: String,
    onQueryChange: (String) -> Unit,
    searching: Boolean,
    note: String,
    hits: List<DrivePlaceHit>,
    recent: List<DrivePlaceHit>,
    onSearch: () -> Unit,
    onPick: (DrivePlaceHit) -> Unit,
    onClose: () -> Unit
) {
    val p = DrivingPalette
    // 顶部输入框
    Box(
        Modifier
            .offset(nx(16), ny(14))
            .size(nx(640), ny(56))
            .clip(RoundedCornerShape(nx(16)))
            .background(p.CardWhite)
    ) {
        Canvas(Modifier.offset(nx(18), ny(18)).size(nx(20))) {
            drawNavGlyph(NavGlyph.SEARCH, p.OnCardSub)
        }
        Box(Modifier.offset(nx(50), ny(16)).size(nx(500), ny(24))) {
            if (query.isEmpty()) {
                Text(
                    tr("搜地点、查路线、找停车场", "Search places, routes, parking"),
                    fontSize = TypeScale.bodyLarge,
                    color = p.OnCardFaint,
                    maxLines = 1
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = p.OnCard,
                    fontSize = TypeScale.bodyLarge,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(p.GoBlue),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Box(
            Modifier
                .offset(nx(596), ny(12))
                .size(nx(32), ny(32))
                .clip(CircleShape)
                .background(p.GoBlueSoft)
                .clickable { onSearch() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(nx(16))) {
                if (searching) drawNavGlyph(NavGlyph.REFRESH, p.GoBlue)
                else drawNavGlyph(NavGlyph.ARROW_RIGHT, p.GoBlue)
            }
        }
    }
    // 取消
    Box(
        Modifier
            .offset(nx(664), ny(14))
            .size(nx(88), ny(56))
            .clip(RoundedCornerShape(nx(16)))
            .background(p.CardWhite)
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            tr("取消", "Cancel"),
            fontSize = TypeScale.bodyLarge,
            color = p.OnCard,
            maxLines = 1
        )
    }

    // 没有搜索结果时（刚进搜索页）显示「最近去过」——高德首页也是这一套；
    // 高度跟内容走（最多 452），否则三条结果会拖出一大块白板压住地图。
    val rows = if (hits.isNotEmpty()) hits else recent
    val listH = (52 + rows.size * 64 + 10).coerceIn(120, 452)
    Box(
        Modifier
            .offset(nx(16), ny(82))
            .size(nx(640), ny(listH))
            .clip(RoundedCornerShape(nx(18)))
            .background(p.CardWhite)
    ) {
        Text(
            when {
                searching -> tr("正在搜索…", "Searching…")
                hits.isNotEmpty() -> tr("找到 ${hits.size} 个地点", "${hits.size} results")
                recent.isNotEmpty() -> tr("最近去过", "Recent")
                else -> tr("搜索建议", "Suggestions")
            },
            Modifier.offset(nx(18), ny(12)).width(nx(600)),
            fontSize = TypeScale.caption,
            color = p.OnCardSub,
            maxLines = 1
        )
        if (rows.isEmpty()) {
            Text(
                note.ifBlank {
                    tr("输入地名、地址或 POI 后按回车", "Type a place, address or POI")
                },
                Modifier.offset(nx(30), ny(190)).width(nx(580)),
                fontSize = TypeScale.body,
                color = p.OnCardFaint,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        } else {
            LazyColumn(Modifier.offset(nx(8), ny(38)).size(nx(624), ny(listH - 44))) {
                // key 用「位次 + 坐标」：同名同地址的连锁店不止一个，
                // 只用坐标会重复；位次本身唯一，能彻底避免 LazyColumn 崩键冲突。
                itemsIndexed(
                    rows,
                    key = { index, hit ->
                        "$index:${hit.point?.lat},${hit.point?.lon},${hit.title}"
                    }
                ) { _, hit ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(ny(64))
                            .clip(RoundedCornerShape(nx(12)))
                            .clickable { onPick(hit) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(nx(34))
                                .clip(RoundedCornerShape(nx(10)))
                                .background(p.CardWhiteSub),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(Modifier.size(nx(17))) {
                                drawNavGlyph(NavGlyph.PIN, p.GoBlue)
                            }
                        }
                        Spacer(Modifier.width(nx(12)))
                        Column(Modifier.weight(1f)) {
                            Text(
                                hit.title,
                                fontSize = TypeScale.body,
                                fontWeight = FontWeight.SemiBold,
                                color = p.OnCard,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(ny(3)))
                            Text(
                                hit.addr.ifBlank { tr("暂无详细地址", "No address") },
                                fontSize = TypeScale.caption,
                                color = p.OnCardFaint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(nx(8)))
                        Text(
                            hit.dist,
                            fontSize = TypeScale.caption,
                            color = p.OnCardSub,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(nx(10)))
                        Box(
                            Modifier
                                .size(nx(52), ny(28))
                                .clip(RoundedCornerShape(nx(8)))
                                .background(p.GoBlueSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                tr("路线", "Go"),
                                fontSize = TypeScale.caption,
                                fontWeight = FontWeight.Bold,
                                color = p.GoBlue,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/* =====================================================================
 *  路线预览：高德选完点后的「三条路线任选」
 * ===================================================================== */

/** 分 → 「12 分钟」里的数字与单位分开给，方便大字号排版。 */
private fun minutesOf(route: DriveRoute): String =
    ((route.durationS + 59) / 60).coerceAtLeast(1).toString()

private fun kmOf(route: DriveRoute): String =
    String.format(Locale.US, "%.1f", route.distanceM / 1000f)

/**
 * 路线预览面板。
 *
 * 高德的流程是「选完目的地 → 先看方案 → 再开始导航」，这块就是那个方案面板：
 * 每条路线给出真实的时间、里程、红绿灯数、过路费，用户选中哪条就画哪条，
 * 上面一排偏好会带着 `strategy` 重新请求高德——不是本地排序糊弄。
 */
@Composable
internal fun DriveRoutePanel(
    nx: (Number) -> Dp,
    ny: (Number) -> Dp,
    targetName: String,
    routes: List<DriveRoute>,
    selected: Int,
    pref: DrivePref,
    planning: Boolean,
    onSelect: (Int) -> Unit,
    onPref: (DrivePref) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    val p = DrivingPalette
    Box(
        Modifier
            .offset(nx(16), ny(82))
            .size(nx(432), ny(452))
            .clip(RoundedCornerShape(nx(18)))
            .background(p.CardWhite)
    ) {
        Text(
            tr("路线方案", "Routes"),
            Modifier.offset(nx(16), ny(12)).width(nx(240)),
            fontSize = TypeScale.title,
            fontWeight = FontWeight.Bold,
            color = p.OnCard,
            maxLines = 1
        )
        Text(
            targetName,
            Modifier.offset(nx(16), ny(36)).width(nx(340)),
            fontSize = TypeScale.caption,
            color = p.OnCardSub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            Modifier
                .offset(nx(388), ny(12))
                .size(nx(28), ny(28))
                .clip(CircleShape)
                .background(p.CardWhiteSub)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(nx(12))) { drawNavGlyph(NavGlyph.CLOSE, p.OnCardSub) }
        }

        if (planning && routes.isEmpty()) {
            Text(
                tr("正在规划路线…", "Planning routes…"),
                Modifier.offset(nx(16), ny(200)).width(nx(400)),
                fontSize = TypeScale.body,
                color = p.OnCardSub,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }

        routes.take(3).forEachIndexed { i, route ->
            val active = i == selected
            Row(
                Modifier
                    .offset(nx(12), ny(64 + i * 84))
                    .size(nx(408), ny(80))
                    .clip(RoundedCornerShape(nx(12)))
                    .background(if (active) p.GoBlueSoft else p.CardWhiteSub)
                    .clickable { onSelect(i) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 选中标识：左侧竖条，和高德一样
                Box(
                    Modifier
                        .width(nx(4))
                        .height(ny(80))
                        .background(if (active) p.GoBlue else Color.Transparent)
                )
                Spacer(Modifier.width(nx(12)))
                Column(Modifier.width(nx(120))) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            minutesOf(route),
                            fontSize = TypeScale.navMid,
                            fontWeight = FontWeight.Bold,
                            color = if (active) p.GoBlue else p.OnCard,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(nx(3)))
                        Text(
                            tr("分钟", "min"),
                            Modifier.padding(bottom = ny(4)),
                            fontSize = TypeScale.tiny,
                            color = p.OnCardSub,
                            maxLines = 1
                        )
                    }
                    Text(
                        tr("${kmOf(route)} 公里", "${kmOf(route)} km"),
                        fontSize = TypeScale.caption,
                        color = p.OnCardSub,
                        maxLines = 1
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        tr(
                            "红绿灯 ${route.lights} 个",
                            "${route.lights} lights"
                        ),
                        fontSize = TypeScale.caption,
                        color = p.OnCardSub,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(ny(4)))
                    Text(
                        if (route.tollsYuan > 0) {
                            tr("过路费 ${route.tollsYuan} 元", "toll ¥${route.tollsYuan}")
                        } else {
                            tr("无过路费", "no toll")
                        },
                        fontSize = TypeScale.caption,
                        color = p.OnCardFaint,
                        maxLines = 1
                    )
                }
                if (i == 0) {
                    Box(
                        Modifier
                            .size(nx(44), ny(22))
                            .clip(RoundedCornerShape(nx(6)))
                            .background(p.GoBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            tr("推荐", "Best"),
                            fontSize = TypeScale.micro,
                            color = p.CardWhite,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.width(nx(10)))
                }
            }
        }

        // 偏好：点了会用对应 strategy 重新请求高德
        Text(
            tr("路线偏好", "Preference"),
            Modifier.offset(nx(16), ny(320)).width(nx(120)),
            fontSize = TypeScale.caption,
            color = p.OnCardSub,
            maxLines = 1
        )
        Row(
            Modifier.offset(nx(12), ny(340)),
            horizontalArrangement = Arrangement.spacedBy(nx(6))
        ) {
            DrivePref.entries.forEach { item ->
                val on = item == pref
                Box(
                    Modifier
                        .height(ny(30))
                        .clip(RoundedCornerShape(nx(8)))
                        .background(if (on) p.GoBlue else p.CardWhiteSub)
                        .clickable { onPref(item) }
                        .padding(horizontal = nx(10)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tr(item.zh, item.en),
                        fontSize = TypeScale.caption,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) p.CardWhite else p.OnCardSub,
                        maxLines = 1
                    )
                }
            }
        }

        Box(
            Modifier
                .offset(nx(12), ny(380))
                .size(nx(408), ny(56))
                .clip(RoundedCornerShape(nx(14)))
                .background(if (routes.isEmpty() || planning) p.CardWhiteSub else p.GoBlue)
                .clickable(enabled = routes.isNotEmpty() && !planning) { onStart() },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(nx(20))) {
                    drawNavGlyph(NavGlyph.STRAIGHT, if (routes.isEmpty()) p.OnCardFaint else p.CardWhite)
                }
                Spacer(Modifier.width(nx(10)))
                Text(
                    if (planning) tr("规划中…", "Planning…")
                    else tr("开始导航", "Start navigation"),
                    fontSize = TypeScale.section,
                    fontWeight = FontWeight.Bold,
                    color = if (routes.isEmpty()) p.OnCardFaint else p.CardWhite,
                    maxLines = 1
                )
            }
        }
    }
}

/* =====================================================================
 *  到达
 * ===================================================================== */

/** 到达卡：目的地、全程真实数据、停车与结束两个出口。 */
@Composable
internal fun DriveArriveCard(
    nx: (Number) -> Dp,
    ny: (Number) -> Dp,
    name: String,
    addr: String,
    km: String,
    minutes: String,
    toll: String,
    onParking: () -> Unit,
    onFinish: () -> Unit
) {
    val p = DrivingPalette
    Box(
        Modifier
            .offset(nx(300), ny(140))
            .size(nx(480), ny(330))
            .clip(RoundedCornerShape(nx(22)))
            .background(p.CardWhite)
    ) {
        Box(
            Modifier
                .offset(nx(24), ny(22))
                .size(nx(40))
                .clip(CircleShape)
                .background(p.HighwayGreen),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(nx(20))) { drawNavGlyph(NavGlyph.CHECK, p.CardWhite) }
        }
        Text(
            tr("已到达目的地", "Arrived"),
            Modifier.offset(nx(76), ny(26)).width(nx(360)),
            fontSize = TypeScale.page,
            fontWeight = FontWeight.Bold,
            color = p.OnCard,
            maxLines = 1
        )
        Text(
            name,
            Modifier.offset(nx(24), ny(76)).width(nx(432)),
            fontSize = TypeScale.coverTitle,
            fontWeight = FontWeight.Bold,
            color = p.OnCard,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            addr,
            Modifier.offset(nx(24), ny(108)).width(nx(432)),
            fontSize = TypeScale.caption,
            color = p.OnCardSub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val cells = listOf(
            km to tr("公里 · 全程", "km total"),
            minutes to tr("分钟 · 用时", "min elapsed"),
            toll to tr("元 · 过路费", "¥ toll")
        )
        cells.forEachIndexed { i, (value, label) ->
            Box(
                Modifier
                    .offset(nx(24 + i * 148), ny(142))
                    .size(nx(136), ny(80))
                    .clip(RoundedCornerShape(nx(14)))
                    .background(p.CardWhiteSub)
            ) {
                Text(
                    value,
                    Modifier.offset(nx(0), ny(14)).width(nx(136)),
                    fontSize = TypeScale.coverTitle,
                    fontWeight = FontWeight.Bold,
                    color = p.OnCard,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Text(
                    label,
                    Modifier.offset(nx(0), ny(46)).width(nx(136)),
                    fontSize = TypeScale.tiny,
                    color = p.OnCardFaint,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(
            Modifier
                .offset(nx(24), ny(246))
                .size(nx(206), ny(54))
                .clip(RoundedCornerShape(nx(14)))
                .background(p.CardWhiteSub)
                .clickable { onParking() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                tr("附近停车场", "Parking nearby"),
                fontSize = TypeScale.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = p.OnCard,
                maxLines = 1
            )
        }
        Box(
            Modifier
                .offset(nx(250), ny(246))
                .size(nx(206), ny(54))
                .clip(RoundedCornerShape(nx(14)))
                .background(p.GoBlue)
                .clickable { onFinish() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                tr("结束导航", "Finish"),
                fontSize = TypeScale.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = p.CardWhite,
                maxLines = 1
            )
        }
    }
}

/* =====================================================================
 *  巡航态：紧凑播放器
 * ===================================================================== */

/**
 * 巡航态的迷你播放器。
 *
 * 原来那是一块 271×454 的竖版大面板，占了半个地图——高德车机上是底部一条小卡，
 * 地图必须始终是主角。收窄之后「封面 / 歌名 / 进度 / 上一首 / 播放 / 下一首 /
 * 队列 / 收起」一样不少，只是排得更紧。
 */
@Composable
internal fun DriveMiniPlayer(
    nx: (Number) -> Dp,
    ny: (Number) -> Dp,
    title: String,
    artist: String,
    progress: Float,
    positionText: String,
    durationText: String,
    isPlaying: Boolean,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onQueue: () -> Unit,
    onCollapse: () -> Unit,
    onSeek: (Float) -> Unit
) {
    val p = DrivingPalette
    Box(
        Modifier
            .offset(nx(600), ny(470))
            // 高度给下限：短屏上按 ny 缩完只剩 80 多 dp，标题/进度/按钮会挤到一起
            .size(nx(464), maxOf(ny(128), 104.dp))
            .clip(RoundedCornerShape(nx(18)))
            .background(Brush.verticalGradient(listOf(p.PanelTop, p.PanelBottom)))
    ) {
        DriveAlbumArt(Modifier.offset(nx(16), ny(20)).size(nx(88)))
        // ‹ 收起
        Box(
            Modifier
                .offset(nx(426), ny(10))
                .size(nx(26))
                .clip(CircleShape)
                .background(p.Tool)
                .clickable { onCollapse() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(nx(6))) {
                val u = size.minDimension
                drawLine(Ink.OnCover, Offset(u * 0.7f, 0f), Offset(u * 0.2f, u * 0.5f), strokeWidth = u * 0.28f, cap = StrokeCap.Round)
                drawLine(Ink.OnCover, Offset(u * 0.2f, u * 0.5f), Offset(u * 0.7f, u), strokeWidth = u * 0.28f, cap = StrokeCap.Round)
            }
        }
        Text(
            title,
            Modifier.offset(nx(118), ny(14)).width(nx(292)),
            fontSize = TypeScale.subtitle,
            fontWeight = FontWeight.Bold,
            color = Ink.OnCover,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            artist,
            Modifier.offset(nx(118), ny(38)).width(nx(292)),
            fontSize = TypeScale.caption,
            color = Ink.OnCoverSub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            positionText,
            Modifier.offset(nx(118), ny(60)).width(nx(42)),
            fontSize = TypeScale.micro,
            color = Ink.OnCoverSub,
            maxLines = 1
        )
        DriveTrack(
            fraction = progress,
            onSeek = onSeek,
            active = p.Teal,
            knob = nx(4),
            modifier = Modifier.offset(nx(162), ny(62)).size(nx(200), ny(16))
        )
        Text(
            durationText,
            Modifier.offset(nx(368), ny(60)).width(nx(42)),
            fontSize = TypeScale.micro,
            color = Ink.OnCoverSub,
            textAlign = TextAlign.End,
            maxLines = 1
        )
        // 四个按钮之间留 16：车机上手指按不准 8dp 的间隙，挤在一起必然误触
        DriveSkipButton(
            forward = false,
            onClick = onPrev,
            glyph = nx(20),
            modifier = Modifier.offset(nx(118), ny(86))
        )
        Box(
            Modifier
                .offset(nx(154), ny(80))
                .size(nx(34))
                .clip(CircleShape)
                .background(Ink.OnCover)
                .clickable { onPlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(nx(14))) {
                if (isPlaying) drawDrivePause(p.PanelBottom) else drawDrivePlay(p.PanelBottom)
            }
        }
        DriveSkipButton(
            forward = true,
            onClick = onNext,
            glyph = nx(20),
            modifier = Modifier.offset(nx(204), ny(86))
        )
        Box(
            Modifier
                .offset(nx(240), ny(82))
                .size(nx(28))
                .clip(CircleShape)
                .background(p.Tool)
                .clickable { onQueue() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(nx(14))) { drawNavGlyph(NavGlyph.LIST, Ink.OnCover) }
        }
    }
}

/* =====================================================================
 *  播放队列
 * ===================================================================== */

/**
 * 播放队列浮层：只列**当前队列**，不是整个曲库。
 *
 * 点一行用同一条队列重放（[onPick] 由调用方负责调 `playFrom`），
 * 队列顺序不变，所以上一首 / 下一首还是进来时那串。
 */
@Composable
internal fun DriveQueueSheet(
    nx: (Number) -> Dp,
    ny: (Number) -> Dp,
    title: String,
    songs: List<LocalSong>,
    currentId: Long?,
    onPick: (LocalSong) -> Unit,
    onClose: () -> Unit
) {
    val p = DrivingPalette
    Box(
        Modifier
            .offset(nx(600), ny(92))
            .size(nx(464), ny(440))
            .clip(RoundedCornerShape(nx(18)))
            .background(Brush.verticalGradient(listOf(p.PanelTop, p.PanelBottom)))
    ) {
        Text(
            title,
            Modifier.offset(nx(18), ny(17)).width(nx(200)),
            fontSize = TypeScale.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = Ink.OnCover,
            maxLines = 1
        )
        Text(
            tr("共 ${songs.size} 首", "${songs.size} tracks"),
            Modifier.offset(nx(200), ny(21)).width(nx(180)),
            fontSize = TypeScale.tiny,
            color = Ink.OnCoverSub,
            textAlign = TextAlign.End,
            maxLines = 1
        )
        Box(
            Modifier
                .offset(nx(416), ny(14))
                .size(nx(30))
                .clip(CircleShape)
                .background(p.Tool)
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(nx(12))) { drawNavGlyph(NavGlyph.CLOSE, Ink.OnCover) }
        }
        Box(Modifier.offset(nx(16), ny(52)).size(nx(432), ny(1)).background(p.Road))
        if (songs.isEmpty()) {
            Column(
                Modifier
                    .offset(nx(16), ny(52))
                    .size(nx(432), ny(360)),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(nx(64))
                        .clip(CircleShape)
                        .background(p.Tool),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(nx(28))) { drawNavGlyph(NavGlyph.SOUND, Ink.OnCoverSub) }
                }
                Spacer(Modifier.height(ny(16)))
                Text(
                    tr("本地音乐还是空的", "Music library is empty"),
                    fontSize = TypeScale.body,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink.OnCover,
                    maxLines = 1
                )
                Spacer(Modifier.height(ny(6)))
                Text(
                    tr("先去音乐页添加歌曲", "Add tracks in Music"),
                    fontSize = TypeScale.caption,
                    color = Ink.OnCoverSub,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        } else {
            LazyColumn(Modifier.offset(nx(10), ny(60)).size(nx(444), ny(370))) {
                items(songs, key = { it.id }) { song ->
                    val active = song.id == currentId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(maxOf(ny(48), 44.dp))
                            .clip(RoundedCornerShape(nx(10)))
                            .background(if (active) p.Tool else Color.Transparent)
                            .clickable { onPick(song) }
                            .padding(horizontal = nx(10)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .width(nx(3))
                                .height(ny(18))
                                .clip(RoundedCornerShape(nx(2)))
                                .background(if (active) p.Teal else Color.Transparent)
                        )
                        Spacer(Modifier.width(nx(9)))
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title.ifBlank { tr("未知曲目", "Unknown") },
                                fontSize = TypeScale.body,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                color = if (active) p.Teal else Ink.OnCover,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(ny(2)))
                            Text(
                                song.artist.ifBlank { tr("未知歌手", "Unknown artist") },
                                fontSize = TypeScale.tiny,
                                color = Ink.OnCoverSub,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (active) {
                            Text(
                                tr("播放中", "Playing"),
                                fontSize = TypeScale.tiny,
                                color = p.Teal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/* =====================================================================
 *  更多：把散落的开关收在一处
 * ===================================================================== */

/** 更多浮层里的一行开关：左边名字，右边状态（开 / 关 / 具体值）。 */
@Composable
private fun MoreRow(
    nx: (Number) -> Dp,
    ny: (Number) -> Dp,
    label: String,
    value: String,
    on: Boolean,
    onClick: () -> Unit
) {
    val p = DrivingPalette
    Row(
        Modifier
            .fillMaxWidth()
            .height(ny(40))
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            Modifier.weight(1f),
            fontSize = TypeScale.body,
            color = Ink.OnCover,
            maxLines = 1
        )
        Text(
            value,
            fontSize = TypeScale.caption,
            color = if (on) p.Teal else Ink.OnCoverSub,
            maxLines = 1
        )
        Spacer(Modifier.width(nx(8)))
        // 开关轨道：开为青色，关为灰
        Box(
            Modifier
                .size(nx(34), ny(18))
                .clip(RoundedCornerShape(ny(9)))
                .background(if (on) p.Teal else p.Tool)
        ) {
            Box(
                Modifier
                    .offset(nx(if (on) 18 else 2), ny(2))
                    .size(nx(14))
                    .clip(CircleShape)
                    .background(Ink.OnCover)
            )
        }
    }
}

/**
 * 「更多」浮层。
 *
 * 这里只放**真的会改变行为**的开关：语音播报（控制 TTS）、正北/车头、
 * 路况图层、模拟导航（沿真实路线推进）、音效预设。以前那两行写死的
 * 「已开启」已经删掉——开关点下去没反应是最糟的体验。
 */
@Composable
internal fun DriveMorePanel(
    nx: (Number) -> Dp,
    ny: (Number) -> Dp,
    voiceOn: Boolean,
    onVoice: () -> Unit,
    northUp: Boolean,
    onNorthUp: () -> Unit,
    trafficOn: Boolean,
    onTraffic: () -> Unit,
    simulating: Boolean,
    onSimulate: () -> Unit,
    accuracy: String,
    eqName: String,
    onSound: () -> Unit,
    onExit: () -> Unit,
    onClose: () -> Unit
) {
    val p = DrivingPalette
    Box(
        Modifier
            .offset(nx(700), ny(92))
            .size(nx(364), ny(440))
            .clip(RoundedCornerShape(nx(18)))
            .background(p.PanelTop)
            .padding(horizontal = nx(18))
    ) {
        Text(
            tr("更多", "More"),
            Modifier.offset(nx(0), ny(14)).width(nx(240)),
            fontSize = TypeScale.title,
            fontWeight = FontWeight.Bold,
            color = Ink.OnCover,
            maxLines = 1
        )
        Box(
            Modifier
                .offset(nx(318), ny(12))
                .size(nx(28))
                .clip(CircleShape)
                .background(p.Tool)
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(nx(12))) { drawNavGlyph(NavGlyph.CLOSE, Ink.OnCover) }
        }
        Column(Modifier.offset(nx(0), ny(52))) {
            MoreRow(nx, ny, tr("语音播报", "Voice guidance"), if (voiceOn) tr("开", "On") else tr("关", "Off"), voiceOn, onVoice)
            MoreRow(nx, ny, tr("正北向上", "North up"), if (northUp) tr("开", "On") else tr("关", "Off"), northUp, onNorthUp)
            MoreRow(nx, ny, tr("路况图层", "Traffic layer"), if (trafficOn) tr("开", "On") else tr("关", "Off"), trafficOn, onTraffic)
            MoreRow(nx, ny, tr("模拟导航", "Simulate"), if (simulating) tr("开", "On") else tr("关", "Off"), simulating, onSimulate)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ny(40))
                    .clickable { onSound() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    tr("音效预设", "Equalizer"),
                    Modifier.weight(1f),
                    fontSize = TypeScale.body,
                    color = Ink.OnCover,
                    maxLines = 1
                )
                Text(
                    eqName,
                    fontSize = TypeScale.caption,
                    color = Ink.OnCoverSub,
                    maxLines = 1
                )
                Spacer(Modifier.width(nx(6)))
                Text("›", fontSize = TypeScale.body, color = Ink.OnCoverSub, maxLines = 1)
            }
            Spacer(Modifier.height(ny(6)))
            Box(Modifier.height(ny(1)).fillMaxWidth().background(p.Road))
            Spacer(Modifier.height(ny(8)))
            Text(
                tr("定位精度 $accuracy", "Accuracy $accuracy"),
                fontSize = TypeScale.caption,
                color = Ink.OnCoverSub,
                maxLines = 1
            )
            Spacer(Modifier.height(ny(10)))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ny(40))
                    .clip(RoundedCornerShape(nx(10)))
                    .background(p.WarnRedSoft.copy(alpha = 0.16f))
                    .clickable { onExit() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    tr("退出驾驶模式", "Exit driving mode"),
                    Modifier.padding(start = nx(10)),
                    fontSize = TypeScale.body,
                    fontWeight = FontWeight.SemiBold,
                    color = p.WarnRed,
                    maxLines = 1
                )
            }
        }
    }
}

/* =====================================================================
 *  行程信息浮层
 * ===================================================================== */

/** 一行「标签 —— 数值」。 */
@Composable
private fun TripLine(nx: (Number) -> Dp, ny: (Number) -> Dp, label: String, value: String) {
    val p = DrivingPalette
    Row(
        Modifier.fillMaxWidth().height(ny(34)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            Modifier.weight(1f),
            fontSize = TypeScale.body,
            color = Ink.OnCoverSub,
            maxLines = 1
        )
        Text(
            value,
            fontSize = TypeScale.body,
            fontWeight = FontWeight.SemiBold,
            color = Ink.OnCover,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 行程浮层：把这些「现在这一刻真的成立」的状态摆出来。
 *
 * 时间、天气、车速、剩余里程、本次行程、路况、定位精度——
 * 每一项都有来源，没数据的写「--」或「未查询」，不摆好看的假读数。
 */
@Composable
internal fun DriveTripSheet(
    nx: (Number) -> Dp,
    ny: (Number) -> Dp,
    clock: String,
    weather: String,
    speed: String,
    remain: String,
    trip: String,
    traffic: String,
    accuracy: String,
    onClose: () -> Unit
) {
    val p = DrivingPalette
    Box(
        Modifier
            .offset(nx(700), ny(92))
            .size(nx(364), ny(300))
            .clip(RoundedCornerShape(nx(18)))
            .background(p.PanelTop)
            .padding(horizontal = nx(18))
    ) {
        Text(
            tr("行程", "Trip"),
            Modifier.offset(nx(0), ny(14)).width(nx(200)),
            fontSize = TypeScale.title,
            fontWeight = FontWeight.Bold,
            color = Ink.OnCover,
            maxLines = 1
        )
        Text(
            "$clock · $weather",
            Modifier.offset(nx(0), ny(40)).width(nx(300)),
            fontSize = TypeScale.caption,
            color = Ink.OnCoverSub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            Modifier
                .offset(nx(318), ny(12))
                .size(nx(28))
                .clip(CircleShape)
                .background(p.Tool)
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(nx(12))) { drawNavGlyph(NavGlyph.CLOSE, Ink.OnCover) }
        }
        Column(Modifier.offset(nx(0), ny(72))) {
            TripLine(nx, ny, tr("当前车速", "Speed"), speed)
            TripLine(nx, ny, tr("剩余里程", "Remaining"), remain)
            TripLine(nx, ny, tr("本次行程", "This trip"), trip)
            TripLine(nx, ny, tr("路况", "Traffic"), traffic)
            TripLine(nx, ny, tr("定位精度", "Accuracy"), accuracy)
        }
    }
}

/* =====================================================================
 *  音效浮层
 * ===================================================================== */

/** 音效预设 + 媒体音量：都是会立刻生效的真实控制。 */
@Composable
internal fun DriveSoundSheet(
    nx: (Number) -> Dp,
    ny: (Number) -> Dp,
    presets: List<String>,
    selected: Int,
    current: String,
    volume: Float,
    onPreset: (Int) -> Unit,
    onVolume: (Float) -> Unit,
    onClose: () -> Unit
) {
    val p = DrivingPalette
    Box(
        Modifier
            .offset(nx(700), ny(92))
            .size(nx(364), ny(300))
            .clip(RoundedCornerShape(nx(18)))
            .background(p.PanelTop)
            .padding(horizontal = nx(18))
    ) {
        Text(
            tr("音效", "Equalizer"),
            Modifier.offset(nx(0), ny(14)).width(nx(200)),
            fontSize = TypeScale.title,
            fontWeight = FontWeight.Bold,
            color = Ink.OnCover,
            maxLines = 1
        )
        Box(
            Modifier
                .offset(nx(318), ny(12))
                .size(nx(28))
                .clip(CircleShape)
                .background(p.Tool)
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(nx(12))) { drawNavGlyph(NavGlyph.CLOSE, Ink.OnCover) }
        }
        Column(Modifier.offset(nx(0), ny(56))) {
            presets.forEachIndexed { idx, label ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(ny(38))
                        .clickable { onPreset(idx) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Canvas(Modifier.size(nx(14))) {
                        val u = size.minDimension
                        drawCircle(
                            if (idx == selected) p.Teal else Ink.OnCoverSub,
                            radius = u * 0.42f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = u * 0.10f)
                        )
                        if (idx == selected) drawCircle(p.Teal, radius = u * 0.20f)
                    }
                    Spacer(Modifier.width(nx(10)))
                    Text(
                        label,
                        fontSize = TypeScale.body,
                        color = if (idx == selected) Ink.OnCover else Ink.OnCoverSub,
                        maxLines = 1
                    )
                }
            }
            Text(
                tr("当前：$current", "Current: $current"),
                fontSize = TypeScale.caption,
                color = Ink.OnCoverSub,
                maxLines = 1
            )
            Spacer(Modifier.height(ny(14)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("音量", "Volume"),
                    Modifier.width(nx(40)),
                    fontSize = TypeScale.caption,
                    color = Ink.OnCoverSub,
                    maxLines = 1
                )
                DriveTrack(
                    fraction = volume,
                    onSeek = onVolume,
                    active = p.Teal,
                    knob = nx(4),
                    modifier = Modifier.width(nx(214)).height(ny(16))
                )
                Spacer(Modifier.width(nx(10)))
                Text(
                    "${(volume * 100).toInt()}%",
                    Modifier.width(nx(44)),
                    fontSize = TypeScale.caption,
                    color = Ink.OnCoverSub,
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
            }
        }
    }
}
