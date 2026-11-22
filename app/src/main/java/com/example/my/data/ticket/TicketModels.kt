package com.example.my.data.ticket

import java.util.Locale

/** 火车票的一个席别余票。value 可能是「有」「无」或具体张数。 */
data class TrainSeat(
    val label: String,
    val value: String
) {
    val available: Boolean get() = value.isNotBlank() && value != "无"

    val display: String
        get() = when {
            !available -> "无票"
            value == "有" -> "有票"
            value.all { it.isDigit() } -> "${value}张"
            else -> value
        }
}

data class TrainTicket(
    val code: String,
    val fromStation: String,
    val toStation: String,
    val departTime: String,
    val arriveTime: String,
    val duration: String,
    val seats: List<TrainSeat>
) {
    val available: Boolean get() = seats.any { it.available }

    /** 只取有票的席别，最多 3 个，避免卡片被 8 个席别撑爆 */
    fun seatSummary(max: Int = 3): List<String> =
        seats.filter { it.available }.take(max).map { "${it.label} ${it.display}" }

    /** AI 对话/复制文本用：保留全部有票席别，用户问「有哪些票」时不能只报前三个 */
    fun seatDetail(max: Int = 8): List<String> =
        seats.filter { it.available }.take(max).map { "${it.label} ${it.display}" }

    fun copyLine(date: String): String =
        "$code $date $fromStation $departTime → $toStation $arriveTime " +
            (seatDetail().joinToString(" / ").ifBlank { "暂无余票" })
}

data class TrainQueryResult(
    val date: String,
    val from: String,
    val to: String,
    val total: Int,
    val note: String,
    val trains: List<TrainTicket>
)

data class FlightOffer(
    val airline: String,
    val airlineCode: String,
    val flightNo: String,
    val fromCode: String,
    val toCode: String,
    val departTime: String,
    val arriveTime: String,
    val arriveDate: String,
    val durationMinutes: Int,
    val stops: Int,
    val price: Double?,
    val currency: String,
    val baggage: String
) {
    val direct: Boolean get() = stops <= 0

    val stopsText: String
        get() = when {
            direct -> "直飞"
            stops == 1 -> "经停 1 次"
            else -> "经停 $stops 次"
        }

    val durationText: String
        get() = when {
            durationMinutes <= 0 -> ""
            durationMinutes < 60 -> "${durationMinutes} 分钟"
            else -> "${durationMinutes / 60}小时${durationMinutes % 60}分"
        }

    val priceText: String
        get() = price?.let {
            val symbol = if (currency.equals("CNY", true) || currency.isBlank()) "¥" else "$currency "
            symbol + String.format(Locale.US, "%.0f", it)
        } ?: "价格待定"

    val baggageText: String
        get() = when {
            baggage.isBlank() -> "行李额以航司为准"
            baggage.contains("no checked bag", true) -> "无免费托运行李"
            baggage.contains("1 checked bag", true) -> "含 1 件托运行李"
            baggage.contains("checked bag", true) -> "含托运行李，额度以航司为准"
            else -> "行李额以航司为准"
        }

    fun copyLine(date: String): String =
        "$flightNo $date $fromCode $departTime → $toCode $arriveTime $priceText"
}

data class FlightQueryResult(
    val offers: List<FlightOffer>,
    val note: String
)

/** 票务查询的统一异常：只带可展示的中文提示，不把上游原始错误页塞进 UI。 */
class TicketException(message: String, cause: Throwable? = null) : Exception(message, cause)
