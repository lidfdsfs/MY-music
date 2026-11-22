package com.example.my

import com.example.my.data.ticket.AirportCodes
import com.example.my.data.ticket.FlightTicketApi
import com.example.my.data.ticket.McpSse
import com.example.my.data.ticket.TicketSecurity
import com.example.my.data.ticket.TrainTicketApi
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 票务解析与网络加固的纯规则测试（不联网）。
 * 真实响应样本放在 `src/test/resources/`，防止字段名或结构变更后悄悄退化成空列表。
 */
class TicketZoneTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.use {
            it.readBytes().toString(Charsets.UTF_8)
        }

    @Test
    fun `真实 12306 MCP 样本能解析车次与余票`() {
        val root = JsonParser.parseString(fixture("train_mcp_sample.json")).asJsonObject
        val result = TrainTicketApi.parseTrain(root, "重庆", "成都", "2026-11-12")
        assertEquals(2, result.trains.size)
        assertEquals(2, result.total)
        assertEquals("重庆", result.from)
        assertTrue(result.note.contains("12306"))
        val first = result.trains.first()
        assertEquals("G8502", first.code)
        assertEquals("06:12", first.departTime)
        assertTrue(first.available)
        assertTrue(first.seatSummary().any { it.contains("二等座") })
        assertFalse(result.trains.last().available)
        assertTrue(first.copyLine("2026-11-12").contains("重庆北"))
    }

    @Test
    fun `真实机票 MCP 样本能解析价格与行李`() {
        val root = JsonParser.parseString(fixture("flight_mcp_sample.json")).asJsonObject
        val offers = FlightTicketApi.parseFlights(root, "CKG", "PVG")
        assertEquals(2, offers.size)
        val first = offers.first()
        assertEquals("Y87538", first.flightNo)
        assertEquals("CKG", first.fromCode)
        assertEquals("PVG", first.toCode)
        assertEquals(721.57, first.price!!, 0.01)
        assertEquals("¥722", first.priceText)
        assertTrue(first.direct)
        assertEquals("无免费托运行李", first.baggageText)
        assertTrue(first.copyLine("2026-11-12").contains("Y87538"))
    }

    @Test
    fun `SSE data 事件被剥出且能读到 result`() {
        val sse = "event: message\n" +
            "data: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}}\n\n"
        assertEquals(1, McpSse.dataEvents(sse).size)
        assertTrue(McpSse.parseResult(sse).has("content"))
        assertTrue(McpSse.parseResult("{\"jsonrpc\":\"2.0\",\"result\":{\"ok\":true}}").has("ok"))
    }

    @Test
    fun `返回文本会去掉脚本标签与控制字符`() {
        assertEquals("G8502", TicketSecurity.sanitize("<script>alert(1)</script>G8502"))
        assertEquals("成都东", TicketSecurity.sanitize("<b>成都东</b>"))
        assertEquals("AB", TicketSecurity.sanitize("A\u202EB\u200B"))
        assertTrue(TicketSecurity.sanitize("x".repeat(200), 20).length == 20)
        assertEquals("", TicketSecurity.sanitize(null))
    }

    @Test
    fun `车次时间与代码只保留合法字符`() {
        assertEquals("G8502", TicketSecurity.code("g 8502!"))
        assertEquals("21:00", TicketSecurity.time("21:00"))
        assertEquals("", TicketSecurity.time("9:00"))
        assertEquals("", TicketSecurity.time("<b>21:00</b>"))
    }

    @Test
    fun `价格只接受有限范围`() {
        assertEquals(721.57, TicketSecurity.parsePrice("721.57")!!, 0.001)
        assertEquals(1234.5, TicketSecurity.parsePrice("￥1,234.5")!!, 0.001)
        assertNull(TicketSecurity.parsePrice("NaN"))
        assertNull(TicketSecurity.parsePrice("999999999"))
        assertNull(TicketSecurity.parsePrice(""))
    }

    @Test
    fun `票务地址只允许固定 HTTPS 主机`() {
        val hosts = setOf("mcp.pianam.cn", "mcp.octotrip.app")
        assertTrue(TicketSecurity.isAllowedHttps("https://mcp.pianam.cn/train-mcp/mcp", hosts))
        assertFalse(TicketSecurity.isAllowedHttps("http://mcp.pianam.cn/train-mcp/mcp", hosts))
        assertFalse(TicketSecurity.isAllowedHttps("https://mcp.pianam.cn.evil.com/x", hosts))
        assertFalse(TicketSecurity.isAllowedHttps("https://user@mcp.pianam.cn/x", hosts))
        assertFalse(TicketSecurity.isAllowedHttps("https://mcp.pianam.cn:8443/x", hosts))
    }

    @Test
    fun `机票价格字段异常不会让整页解析崩溃`() {
        val root = JsonParser.parseString(
            "{\"results\":[{\"flight_numbers\":[\"X1\"],\"outbound\":{\"departure\":\"CKG\"," +
                "\"arrival\":\"PVG\",\"departure_time\":\"09:00\"}," +
                "\"price\":{\"amount\":700}}]}"
        ).asJsonObject
        val offers = FlightTicketApi.parseFlights(root, "CKG", "PVG")
        assertEquals(1, offers.size)
        assertNull(offers.first().price)
    }

    @Test
    fun `城市名能解析成机场三字码`() {
        assertEquals(listOf("CKG"), AirportCodes.resolve("重庆"))
        assertEquals(listOf("CKG"), AirportCodes.resolve("重庆 (CKG)"))
        assertTrue(AirportCodes.resolve("上海").containsAll(listOf("SHA", "PVG")))
        assertTrue(AirportCodes.hint("上海").contains("SHA"))
        assertTrue(AirportCodes.resolve("火星").isEmpty())
    }
}
