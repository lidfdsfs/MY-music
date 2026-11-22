package com.example.my.data.ticket

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 12306 余票查询（经免费只读 MCP）。App 不碰官方接口，也不做购票动作。 */
object TrainTicketApi {

    const val ENDPOINT = "https://mcp.pianam.cn/train-mcp/mcp"
    val HOSTS = setOf("mcp.pianam.cn")

    private val SEAT_ORDER = listOf(
        "商务/特等座", "一等座", "二等座", "软卧", "软座", "硬卧", "硬座", "无座"
    )

    suspend fun query(from: String, to: String, date: String): TrainQueryResult =
        withContext(Dispatchers.IO) {
            val fromCity = TicketSecurity.sanitize(from, 24)
            val toCity = TicketSecurity.sanitize(to, 24)
            if (fromCity.isBlank() || toCity.isBlank()) throw TicketException("请填写出发地和到达地")
            if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date)) {
                throw TicketException("日期格式不正确")
            }
            val args = JsonObject().apply {
                addProperty("from_city", fromCity)
                addProperty("to_city", toCity)
                addProperty("date", date)
            }
            val root = McpSse.callTool(ENDPOINT, HOSTS, "query_train_tickets", args)
            parseTrain(root, fromCity, toCity, date)
        }

    fun parseTrain(root: JsonObject, fromFallback: String = "", toFallback: String = "", dateFallback: String = ""): TrainQueryResult {
        val date = root.text("日期").ifBlank { dateFallback }
        val from = root.text("出发").ifBlank { fromFallback }
        val to = root.text("到达").ifBlank { toFallback }
        val note = root.text("说明", 180)
        val array = root.getAsJsonArray("车次") ?: JsonArray()
        val trains = ArrayList<TrainTicket>(array.size())

        for (element in array) {
            if (!element.isJsonObject) continue
            val item = element.asJsonObject
            val code = TicketSecurity.code(item.text("车次"), 12)
            if (code.isBlank()) continue
            val seatsObj = item.get("余票")?.takeIf { it.isJsonObject }?.asJsonObject
            val seats = SEAT_ORDER.mapNotNull { name ->
                val value = seatsObj?.text(name, 8).orEmpty()
                if (value.isBlank()) null else TrainSeat(name, value)
            }
            val fromStation = item.text("出发站", 24)
            val toStation = item.text("到达站", 24)
            trains += TrainTicket(
                code = code,
                fromStation = fromStation,
                toStation = toStation,
                departTime = TicketSecurity.time(item.text("出发时间", 8)),
                arriveTime = TicketSecurity.time(item.text("到达时间", 8)),
                duration = item.text("历时", 16),
                seats = seats
            )
        }

        val total = root.int("车次总数", trains.size)
        if (trains.isEmpty() && note.isBlank()) {
            throw TicketException("没有查到车次，请检查城市名或日期")
        }
        return TrainQueryResult(
            date = date,
            from = from,
            to = to,
            total = total,
            note = note,
            trains = trains
        )
    }
}

internal fun JsonObject.text(key: String, max: Int = TicketSecurity.MAX_TEXT): String =
    get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString
        ?.let { TicketSecurity.sanitize(it, max) }.orEmpty()

internal fun JsonObject.int(key: String, fallback: Int): Int =
    get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
        ?.let { runCatching { it.asInt }.getOrNull() } ?: fallback
