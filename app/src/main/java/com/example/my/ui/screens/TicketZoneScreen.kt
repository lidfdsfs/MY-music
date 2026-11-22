package com.example.my.ui.screens

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DirectionsRailway
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my.data.AiPresets
import com.example.my.data.ticket.AirportCodes
import com.example.my.data.ticket.FlightOffer
import com.example.my.data.ticket.FlightQueryResult
import com.example.my.data.ticket.TicketAi
import com.example.my.data.ticket.TicketException
import com.example.my.data.ticket.TicketRepository
import com.example.my.data.ticket.TrainQueryResult
import com.example.my.data.ticket.TrainTicket
import com.example.my.state.AppState
import com.example.my.ui.components.AppTopBar
import com.example.my.ui.components.BackActionRow
import com.example.my.ui.components.CircleIconButton
import com.example.my.ui.components.PrimaryButton
import com.example.my.ui.i18n.tr
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.MainGradient
import com.example.my.ui.theme.Radius
import com.example.my.ui.theme.Text
import com.example.my.ui.theme.cardShadow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class TicketMode { TRAIN, FLIGHT }

private val API_DATE = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val PRETTY_DATE = SimpleDateFormat("M月d日 EEEE", Locale.CHINA)

private const val DAY_MS = 24L * 60L * 60L * 1000L

private fun todayIso(): String = API_DATE.format(Date())

private fun shiftDate(iso: String, days: Int): String {
    val cal = Calendar.getInstance()
    val parsed = runCatching { API_DATE.parse(iso) }.getOrNull() ?: Date()
    cal.time = parsed
    cal.add(Calendar.DAY_OF_YEAR, days)
    return API_DATE.format(cal.time)
}

private fun prettyDate(iso: String): String =
    runCatching { PRETTY_DATE.format(API_DATE.parse(iso)!!) }.getOrDefault(iso)

private fun dateOffsetLabel(iso: String, today: String): String = when (iso) {
    today -> "今天"
    shiftDate(today, 1) -> "明天"
    shiftDate(today, 2) -> "后天"
    else -> prettyDate(iso).substringBefore(" ")
}

/**
 * 行程页的「购票专区」入口卡片：只做导航，不在这里发任何请求。
 */
@Composable
fun TicketZoneEntry(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(Brush.linearGradient(MainGradient))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.DirectionsRailway,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tr("购票专区", "Tickets"),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                tr("火车票实时余票 · 机票实时比价", "Train availability · Flight fares"),
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 11.sp
            )
        }
        Text(tr("进入", "Open"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun TicketZoneScreen(state: AppState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { todayIso() }

    var mode by remember { mutableStateOf(TicketMode.TRAIN) }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(shiftDate(today, 1)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var trains by remember { mutableStateOf<TrainQueryResult?>(null) }
    var flights by remember { mutableStateOf<FlightQueryResult?>(null) }
    var onlyAvailable by remember { mutableStateOf(true) }

    // 查询完成后，在真实结果里做一次 AI 推荐。
    var advice by remember { mutableStateOf<TicketAi.Advice?>(null) }
    var adviceLoading by remember { mutableStateOf(false) }

    fun aiEndpoint() = state.aiFeatureEndpoint(com.example.my.data.AiFeature.TICKET)
        .takeIf { it.ready } ?: AiPresets.builtin

    suspend fun runSearch(kind: TicketMode, f: String, t: String, d: String) {
        if (d < today) {
            error = tr("日期不能早于今天")
            return
        }
        if (kind == TicketMode.TRAIN && d > shiftDate(today, 15)) {
            error = tr("12306 预售期为 15 天，最晚可查 ${shiftDate(today, 15)}")
            trains = null
            return
        }
        loading = true
        error = null
        trains = null
        flights = null
        advice = null
        var trainResult: TrainQueryResult? = null
        var flightResult: FlightQueryResult? = null
        try {
            if (kind == TicketMode.TRAIN) {
                trainResult = TicketRepository.trains(f, t, d)
                trains = trainResult
            } else {
                flightResult = TicketRepository.flights(f, t, d)
                flights = flightResult
            }
        } catch (e: TicketException) {
            error = e.message ?: tr("查询失败，请稍后重试")
        } catch (e: Exception) {
            error = tr("查询失败，请稍后重试")
        } finally {
            loading = false
        }

        if (trainResult != null || flightResult != null) {
            adviceLoading = true
            advice = TicketAi.advise(
                aiEndpoint(),
                TicketAi.Query(
                    kind = if (kind == TicketMode.TRAIN) TicketAi.Kind.TRAIN else TicketAi.Kind.FLIGHT,
                    from = f,
                    to = t,
                    date = d
                ),
                trainResult,
                flightResult
            )
            adviceLoading = false
        }
    }

    fun copyText(text: String) {
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("购票信息", text))
        state.toast(tr("已复制，可粘贴到 12306 或航司 App"))
    }

    fun showDatePicker() {
        val cal = Calendar.getInstance()
        runCatching { API_DATE.parse(date) }.getOrNull()?.let { cal.time = it }
        val dialog = DatePickerDialog(
            ctx,
            { _, y, m, d ->
                date = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                trains = null
                flights = null
                advice = null
                error = null
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        val minCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        dialog.datePicker.minDate = minCal.timeInMillis
        dialog.datePicker.maxDate = minCal.timeInMillis +
            DAY_MS * (if (mode == TicketMode.TRAIN) 16 else 331) - 1
        dialog.show()
    }

    fun search() {
        val f = from.trim()
        val t = to.trim()
        when {
            f.isBlank() || t.isBlank() -> {
                state.toast(tr("请填写出发地和到达地"))
                return
            }
            f == t -> {
                state.toast(tr("出发地和到达地不能相同"))
                return
            }
            date < today -> {
                state.toast(tr("日期不能早于今天"))
                return
            }
            mode == TicketMode.TRAIN && date > shiftDate(today, 15) -> {
                error = tr("12306 预售期为 15 天，最晚可查 ${shiftDate(today, 15)}")
                trains = null
                return
            }
        }
        scope.launch { runSearch(mode, f, t, date) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg.Background)
    ) {
        AppTopBar(
            title = tr("购票专区", "Tickets"),
            subtitle = tr("火车票 · 机票", "Train · Flight")
        )
        BackActionRow(onBack = { state.closeScreen() })

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ModeTabs(mode) { next ->
                    mode = next
                    error = null
                    trains = null
                    flights = null
                    advice = null
                }
            }
            item {
                SearchPanel(
                    mode = mode,
                    from = from,
                    to = to,
                    date = date,
                    today = today,
                    loading = loading,
                    onFromChange = { from = it; error = null; trains = null; flights = null; advice = null },
                    onToChange = { to = it; error = null; trains = null; flights = null; advice = null },
                    onSwap = {
                        val old = from
                        from = to
                        to = old
                        trains = null
                        flights = null
                        advice = null
                        error = null
                    },
                    onDateClick = { showDatePicker() },
                    onQuickDate = {
                        date = it
                        trains = null
                        flights = null
                        advice = null
                        error = null
                    },
                    onSearch = { search() }
                )
            }
            item { PrivacyNote() }

            if (loading) {
                item { LoadingCard(mode) }
            }
            error?.let { message ->
                item { ErrorCard(message) }
            }

            trains?.let { result ->
                item {
                    TrainSummary(
                        result = result,
                        onlyAvailable = onlyAvailable,
                        onToggle = { onlyAvailable = !onlyAvailable }
                    )
                }
                if (adviceLoading || advice != null) {
                    item { AiAdviceCard(advice = advice, loading = adviceLoading) }
                }
                val visible = result.trains.filter { !onlyAvailable || it.available }
                if (visible.isEmpty()) {
                    item { EmptyCard(tr("当前筛选下没有余票，试试关闭「只看有票」")) }
                } else {
                    items(visible) { ticket ->
                        TrainCard(
                            ticket = ticket,
                            recommended = advice?.pick == ticket.code,
                            onCopy = { copyText(ticket.copyLine(result.date)) }
                        )
                    }
                }
                if (result.note.isNotBlank()) {
                    item { SourceNote(result.note) }
                }
            }

            flights?.let { result ->
                item { FlightSummary(result) }
                if (adviceLoading || advice != null) {
                    item { AiAdviceCard(advice = advice, loading = adviceLoading) }
                }
                if (result.offers.isEmpty()) {
                    item { EmptyCard(tr("没有查到航班，请换城市或日期再试")) }
                } else {
                    items(result.offers) { offer ->
                        FlightCard(
                            offer = offer,
                            recommended = advice?.pick == offer.flightNo,
                            onCopy = { copyText(offer.copyLine(date)) }
                        )
                    }
                    if (result.note.isNotBlank()) {
                        item { SourceNote(result.note) }
                    }
                }
            }

            item { Disclaimer() }
        }
    }
}

@Composable
private fun AiAdviceCard(advice: TicketAi.Advice?, loading: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Brand.Teal.copy(alpha = 0.08f))
            .border(1.dp, Brand.Teal.copy(alpha = 0.20f), RoundedCornerShape(Radius.md))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = Brand.Teal,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(9.dp))
            Text(tr("AI 正在比时间、价格和余票…", "AI is comparing time, price and seats…"), color = Text.Secondary, fontSize = 11.5.sp)
        } else if (advice != null) {
            Column(Modifier.weight(1f)) {
                Text(
                    tr("AI 推荐 · ${advice.title}", "AI pick · ${advice.title}"),
                    color = Brand.Teal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(3.dp))
                Text(advice.reason, color = Text.Primary, fontSize = 11.5.sp, lineHeight = 16.sp)
                if (advice.alternatives.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr("备选：${advice.alternatives.joinToString("；")}", "Also: ${advice.alternatives.joinToString("; ")}"),
                        color = Text.Muted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AiPickPill() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(Brand.Terracotta.copy(alpha = 0.13f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(tr("AI 推荐", "AI pick"), color = Brand.Terracotta, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ModeTabs(mode: TicketMode, onChange: (TicketMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.pill))
            .background(Bg.Surface)
            .padding(4.dp)
    ) {
        TicketMode.values().forEach { item ->
            val selected = mode == item
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(Radius.pill))
                    .then(
                        if (selected) Modifier.background(Brush.linearGradient(MainGradient))
                        else Modifier
                    )
                    .clickable { onChange(item) },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (item == TicketMode.TRAIN) Icons.Outlined.DirectionsRailway
                        else Icons.Outlined.Flight,
                        contentDescription = null,
                        tint = if (selected) Color.White else Text.Secondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (item == TicketMode.TRAIN) tr("火车票", "Train") else tr("机票", "Flight"),
                        color = if (selected) Color.White else Text.Secondary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPanel(
    mode: TicketMode,
    from: String,
    to: String,
    date: String,
    today: String,
    loading: Boolean,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    onSwap: () -> Unit,
    onDateClick: () -> Unit,
    onQuickDate: (String) -> Unit,
    onSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Bg.Surface)
            .cardShadow(RoundedCornerShape(Radius.lg))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            TicketField(
                value = from,
                onValueChange = onFromChange,
                label = if (mode == TicketMode.TRAIN) tr("出发城市 / 车站") else tr("出发城市 / 机场"),
                placeholder = tr("如 重庆"),
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Bg.SurfaceVariant)
                    .clickable(onClick = onSwap),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.SwapHoriz,
                    contentDescription = tr("交换出发地和到达地"),
                    tint = Brand.Terracotta,
                    modifier = Modifier.size(19.dp)
                )
            }
            TicketField(
                value = to,
                onValueChange = onToChange,
                label = if (mode == TicketMode.TRAIN) tr("到达城市 / 车站") else tr("到达城市 / 机场"),
                placeholder = tr("如 成都"),
                modifier = Modifier.weight(1f)
            )
        }

        if (mode == TicketMode.FLIGHT) {
            val fromHint = AirportCodes.hint(from)
            val toHint = AirportCodes.hint(to)
            if (fromHint.isNotBlank() || toHint.isNotBlank()) {
                Text(
                    listOf(fromHint, toHint).filter { it.isNotBlank() }.joinToString(" · "),
                    color = Text.Muted,
                    fontSize = 10.5.sp
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.sm))
                .background(Bg.SurfaceVariant)
                .clickable(onClick = onDateClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = Brand.Terracotta,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                dateOffsetLabel(date, today),
                color = Text.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(8.dp))
            Text(prettyDate(date), color = Text.Muted, fontSize = 11.5.sp)
            Spacer(Modifier.weight(1f))
            Text(tr("更改", "Change"), color = Brand.Terracotta, fontSize = 11.5.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 1, 2).forEach { offset ->
                val d = shiftDate(today, offset)
                QuickDateChip(
                    text = dateOffsetLabel(d, today),
                    selected = d == date,
                    onClick = { onQuickDate(d) }
                )
            }
        }

        PrimaryButton(
            text = when {
                loading -> tr("查询中…", "Searching…")
                mode == TicketMode.TRAIN -> tr("查询火车票", "Search trains")
                else -> tr("查询机票", "Search flights")
            },
            onClick = onSearch,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            if (mode == TicketMode.TRAIN)
                tr("支持城市名或车站名；只查余票，不提供购票。")
            else
                tr("支持城市名、机场名或三字码；上海/北京/成都等会同时查多个机场。"),
            color = Text.Muted,
            fontSize = 10.5.sp
        )
    }
}

@Composable
private fun TicketField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(label, color = Text.Muted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(5.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Text.Primary, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(Brand.Terracotta),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(Bg.SurfaceVariant)
                        .border(1.dp, Bg.Outline, RoundedCornerShape(Radius.sm))
                        .padding(horizontal = 11.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isBlank()) {
                        Text(placeholder, color = Text.Muted, fontSize = 13.sp)
                    }
                    inner()
                }
            }
        )
    }
}

@Composable
private fun QuickDateChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(if (selected) Brand.Terracotta.copy(alpha = 0.12f) else Bg.SurfaceVariant)
            .border(
                1.dp,
                if (selected) Brand.Terracotta.copy(alpha = 0.45f) else Color.Transparent,
                RoundedCornerShape(Radius.pill)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            color = if (selected) Brand.Terracotta else Text.Secondary,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun PrivacyNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Bg.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = Brand.Teal,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            tr(
                "隐私保护：AI 找票会把这句话和班次摘要发给所选 AI 服务商；票务 MCP 只发送出发地、到达地和日期，不带账号、Cookie、定位或设备标识。返回内容只按 JSON 读取，不执行网页代码。第三方仍能看到网络出口 IP。",
                "Privacy: AI search sends your sentence and a summary of results to the selected AI provider. Ticket MCPs only receive origin, destination and date — no account, cookie, location or device ID. Responses are parsed as JSON only; no web code is executed. The third-party still sees your network IP."
            ),
            color = Text.Secondary,
            fontSize = 10.5.sp,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun LoadingCard(mode: TicketMode) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Bg.Surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            color = Brand.Terracotta,
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            if (mode == TicketMode.TRAIN) tr("正在向 12306 查询余票…") else tr("正在查询实时机票价格…"),
            color = Text.Secondary,
            fontSize = 12.5.sp
        )
    }
}

@Composable
private fun ErrorCard(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Brand.Terracotta.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = Brand.Terracotta,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(message, color = Text.Primary, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun EmptyCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Bg.Surface)
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = Text.Muted, fontSize = 12.5.sp)
    }
}

@Composable
private fun TrainSummary(
    result: TrainQueryResult,
    onlyAvailable: Boolean,
    onToggle: () -> Unit
) {
    val shown = result.trains.count { !onlyAvailable || it.available }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${result.from} → ${result.to}",
                color = Text.Primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                tr("共 ${result.total} 趟 · 当前显示 $shown 趟", "${result.total} trains · showing $shown"),
                color = Text.Muted,
                fontSize = 11.sp
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.pill))
                .background(if (onlyAvailable) Brand.Teal.copy(alpha = 0.12f) else Bg.SurfaceVariant)
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(
                if (onlyAvailable) tr("只看有票", "Available only") else tr("显示全部", "Show all"),
                color = if (onlyAvailable) Brand.Teal else Text.Secondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TrainCard(ticket: TrainTicket, recommended: Boolean = false, onCopy: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Bg.Surface)
            .cardShadow(RoundedCornerShape(Radius.md))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(70.dp)) {
                Text(
                    ticket.departTime.ifBlank { "--:--" },
                    color = Text.Primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    ticket.fromStation.ifBlank { "—" },
                    color = Text.Secondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ticket.code,
                        color = Brand.Terracotta,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (recommended) {
                        Spacer(Modifier.width(5.dp))
                        AiPickPill()
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    ticket.duration.ifBlank { tr("历时未知") },
                    color = Text.Muted,
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(1.dp)
                        .background(Bg.Outline)
                )
            }
            Column(Modifier.width(70.dp), horizontalAlignment = Alignment.End) {
                Text(
                    ticket.arriveTime.ifBlank { "--:--" },
                    color = Text.Primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    ticket.toStation.ifBlank { "—" },
                    color = Text.Secondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val seats = ticket.seatSummary()
            if (seats.isEmpty()) {
                Text(tr("暂无余票", "No seats"), color = Text.Muted, fontSize = 11.sp)
            } else {
                seats.forEach { label ->
                    SeatPill(label)
                    Spacer(Modifier.width(6.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            CircleIconButton(
                icon = Icons.Outlined.ContentCopy,
                contentDescription = tr("复制车次"),
                onClick = onCopy,
                size = 30.dp,
                background = Bg.SurfaceVariant,
                border = false,
                tint = Text.Secondary
            )
        }
    }
}

@Composable
private fun SeatPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(Brand.Teal.copy(alpha = 0.11f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, color = Brand.Teal, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FlightSummary(result: FlightQueryResult) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            tr("共 ${result.offers.size} 个航班", "${result.offers.size} flights"),
            color = Text.Primary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            tr("按价格从低到高排列", "Sorted by price"),
            color = Text.Muted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun FlightCard(offer: FlightOffer, recommended: Boolean = false, onCopy: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Bg.Surface)
            .cardShadow(RoundedCornerShape(Radius.md))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(Brand.Terracotta.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    offer.airlineCode.ifBlank { offer.flightNo.take(2) },
                    color = Brand.Terracotta,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    airlineName(offer),
                    color = Text.Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(offer.flightNo, color = Text.Muted, fontSize = 10.5.sp)
            }
            if (recommended) {
                Spacer(Modifier.width(5.dp))
                AiPickPill()
                Spacer(Modifier.width(5.dp))
            }
            Text(
                offer.priceText,
                color = Brand.Terracotta,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(64.dp)) {
                Text(
                    offer.departTime.ifBlank { "--:--" },
                    color = Text.Primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(offer.fromCode.ifBlank { "—" }, color = Text.Secondary, fontSize = 11.sp)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("→", color = Brand.Terracotta, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    listOf(offer.durationText, offer.stopsText).filter { it.isNotBlank() }.joinToString(" · "),
                    color = Text.Muted,
                    fontSize = 10.sp
                )
            }
            Column(Modifier.width(64.dp), horizontalAlignment = Alignment.End) {
                Text(
                    offer.arriveTime.ifBlank { "--:--" },
                    color = Text.Primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(offer.toCode.ifBlank { "—" }, color = Text.Secondary, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                offer.baggageText,
                color = Text.Muted,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(tr("含税参考", "Tax incl."), color = Text.Muted, fontSize = 10.sp)
            Spacer(Modifier.width(8.dp))
            CircleIconButton(
                icon = Icons.Outlined.ContentCopy,
                contentDescription = tr("复制航班"),
                onClick = onCopy,
                size = 30.dp,
                background = Bg.SurfaceVariant,
                border = false,
                tint = Text.Secondary
            )
        }
    }
}

private fun airlineName(offer: FlightOffer): String {
    val cn = when (offer.airlineCode.uppercase()) {
        "CA" -> "中国国航"
        "MU" -> "东方航空"
        "CZ" -> "南方航空"
        "HU" -> "海南航空"
        "3U" -> "四川航空"
        "MF" -> "厦门航空"
        "SC" -> "山东航空"
        "ZH" -> "深圳航空"
        "9C" -> "春秋航空"
        "Y8" -> "金鹏航空"
        "GS" -> "天津航空"
        "8L" -> "祥鹏航空"
        "PN" -> "西部航空"
        "G5" -> "华夏航空"
        "EU" -> "成都航空"
        "TV" -> "西藏航空"
        "JD" -> "首都航空"
        "HO" -> "吉祥航空"
        "FM" -> "上海航空"
        "KN" -> "中联航"
        "NS" -> "河北航空"
        "QW" -> "青岛航空"
        "A6" -> "湖南航空"
        "GT" -> "桂林航空"
        "GJ" -> "长龙航空"
        "BK" -> "奥凯航空"
        "DZ" -> "东海航空"
        "UQ" -> "乌鲁木齐航空"
        "KY" -> "昆明航空"
        "DR" -> "瑞丽航空"
        else -> ""
    }
    return if (cn.isNotBlank()) cn else offer.airline.ifBlank { offer.airlineCode.ifBlank { offer.flightNo } }
}

@Composable
private fun SourceNote(note: String) {
    Text(
        note,
        color = Text.Muted,
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun Disclaimer() {
    Text(
        tr(
            "数据说明：火车票余票来自 12306 公开查询（经第三方免费 MCP），仅供查询，购票请前往 12306 官方 App；机票价格为第三方实时比价参考，不含机建燃油，以下单页为准。App 不保存查询记录，也不提供代购。",
            "Data: train availability comes from 12306 public data via a third-party free MCP; search only, buy on the official 12306 app. Flight fares are third-party live comparisons, excluding taxes/fees. No query history is stored."
        ),
        color = Text.Muted,
        fontSize = 10.5.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}
