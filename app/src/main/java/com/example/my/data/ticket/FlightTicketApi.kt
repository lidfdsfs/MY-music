package com.example.my.data.ticket

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** OctoTrip 免费机票实时比价 MCP（无需注册、无需 API Key）。 */
object FlightTicketApi {

    const val ENDPOINT = "https://mcp.octotrip.app/flights/mcp"
    val HOSTS = setOf("mcp.octotrip.app")

    suspend fun search(from: String, to: String, date: String): FlightQueryResult =
        withContext(Dispatchers.IO) {
            val fromCity = TicketSecurity.sanitize(from, 24)
            val toCity = TicketSecurity.sanitize(to, 24)
            if (fromCity.isBlank() || toCity.isBlank()) throw TicketException("请填写出发地和到达地")
            if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date)) {
                throw TicketException("日期格式不正确")
            }

            val origins = AirportCodes.resolve(fromCity).ifEmpty { listOf(fromCity) }
            val destinations = AirportCodes.resolve(toCity).ifEmpty { listOf(toCity) }
            // 一个城市多机场时最多并发 4 个组合；再多多半是输入异常，也没有展示价值
            val pairs = origins.flatMap { o -> destinations.map { d -> o to d } }.take(4)

            val outcomes = coroutineScope {
                pairs.map { (origin, destination) ->
                    async(Dispatchers.IO) {
                        try {
                            Result.success(queryOne(origin, destination, date))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    }
                }.awaitAll()
            }

            val offers = outcomes.flatMap { it.getOrNull().orEmpty() }
                .distinctBy { "${it.flightNo}|${it.fromCode}|${it.toCode}|${it.departTime}" }
                .sortedWith(compareBy<FlightOffer> { it.price ?: Double.MAX_VALUE }.thenBy { it.departTime })
                .take(60)

            if (offers.isEmpty()) {
                val firstError = outcomes.firstNotNullOfOrNull { it.exceptionOrNull() }
                throw when (firstError) {
                    is TicketException -> firstError
                    else -> TicketException("没有查到航班，请检查城市/机场或日期")
                }
            }
            FlightQueryResult(
                offers = offers,
                note = "价格为含税参考价，不含机建燃油；以下单页为准"
            )
        }

    private fun queryOne(origin: String, destination: String, date: String): List<FlightOffer> {
        val args = JsonObject().apply {
            addProperty("origin", origin)
            addProperty("destination", destination)
            addProperty("departure_date", date)
            addProperty("adults", 1)
            addProperty("children", 0)
            addProperty("infants", 0)
            addProperty("trip_class", "Y")
            addProperty("currency", "CNY")
            addProperty("locale", "zh")
        }
        val root = McpSse.callTool(ENDPOINT, HOSTS, "search", args)
        val error = root.text("error", 40)
        if (error.isNotBlank()) {
            if (error == "disambiguation_needed") {
                val airports = root.getAsJsonArray("airports")
                val codes = airports?.mapNotNull { el ->
                    if (!el.isJsonObject) null else {
                        val obj = el.asJsonObject
                        val code = TicketSecurity.code(obj.text("code", 8))
                        val name = obj.text("name", 40)
                        if (code.isBlank()) null else if (name.isBlank()) code else "$code $name"
                    }
                }.orEmpty().take(5)
                val hint = if (codes.isEmpty()) "" else "：${codes.joinToString(" / ")}"
                throw TicketException("“$origin”有多个机场，请直接在出发地填写三字码$hint")
            }
            throw TicketException("机票查询失败，请检查城市或机场名")
        }
        return parseFlights(root, origin, destination)
    }

    fun parseFlights(root: JsonObject, originFallback: String = "", destinationFallback: String = ""): List<FlightOffer> {
        val array = root.getAsJsonArray("results") ?: JsonArray()
        val out = ArrayList<FlightOffer>(array.size())
        for (element in array) {
            if (!element.isJsonObject) continue
            val item = element.asJsonObject
            val outbound = item.get("outbound")?.takeIf { it.isJsonObject }?.asJsonObject
            val legs = outbound?.getAsJsonArray("legs")
            val firstLeg = legs?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject

            val flightNumbers = item.getAsJsonArray("flight_numbers")?.mapNotNull { el ->
                if (el.isJsonPrimitive) TicketSecurity.code(el.asString, 10) else null
            }.orEmpty()
            val flightNo = flightNumbers.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: TicketSecurity.code(firstLeg?.text("flight_number"), 10)
            if (flightNo.isBlank()) continue

            val fromCode = TicketSecurity.code(
                outbound?.text("departure", 8).orEmpty().ifBlank { originFallback }, 8
            )
            val toCode = TicketSecurity.code(
                outbound?.text("arrival", 8).orEmpty().ifBlank { destinationFallback }, 8
            )
            val departTime = TicketSecurity.time(outbound?.text("departure_time", 8))
            val arriveTime = TicketSecurity.time(outbound?.text("arrival_time", 8))
            val price = TicketSecurity.parsePrice(
                item.get("price")?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString
            )
            val stops = item.int("stops", outbound?.int("stops", 0) ?: 0).coerceIn(0, 5)
            val duration = item.int(
                "total_duration_minutes",
                outbound?.int("duration_minutes", 0) ?: 0
            ).coerceIn(0, 48 * 60)

            out += FlightOffer(
                airline = item.text("airline", 40),
                airlineCode = TicketSecurity.code(item.text("airline_code", 6), 6),
                flightNo = flightNo,
                fromCode = fromCode,
                toCode = toCode,
                departTime = departTime,
                arriveTime = arriveTime,
                arriveDate = outbound?.text("arrival_date", 12).orEmpty(),
                durationMinutes = duration,
                stops = stops,
                price = price,
                currency = item.text("currency", 6).ifBlank { "CNY" },
                baggage = item.text("baggage", 80)
            )
        }
        return out
    }
}
