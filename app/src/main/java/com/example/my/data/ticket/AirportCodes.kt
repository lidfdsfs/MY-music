package com.example.my.data.ticket

/**
 * 常见城市 → 机场三字码。
 *
 * OctoTrip 机票 MCP 只认 IATA 或机场名，直接传中文城市名会返回
 * `disambiguation_needed`。这里只放常见国内/港澳台城市，命中多个机场时
 * 由 [FlightTicketApi] 并行查询后合并；表里没有的城市会原样交给服务端，
 * 若服务端要求消歧，UI 会提示用户直接填三字码。
 */
object AirportCodes {

    private val CITY_TO_CODES: Map<String, List<String>> = mapOf(
        "重庆" to listOf("CKG"),
        "北京" to listOf("PEK", "PKX"),
        "上海" to listOf("SHA", "PVG"),
        "广州" to listOf("CAN"),
        "深圳" to listOf("SZX"),
        "成都" to listOf("CTU", "TFU"),
        "杭州" to listOf("HGH"),
        "西安" to listOf("XIY"),
        "昆明" to listOf("KMG"),
        "南京" to listOf("NKG"),
        "武汉" to listOf("WUH"),
        "长沙" to listOf("CSX"),
        "厦门" to listOf("XMN"),
        "青岛" to listOf("TAO"),
        "天津" to listOf("TSN"),
        "三亚" to listOf("SYX"),
        "海口" to listOf("HAK"),
        "哈尔滨" to listOf("HRB"),
        "沈阳" to listOf("SHE"),
        "大连" to listOf("DLC"),
        "济南" to listOf("TNA"),
        "郑州" to listOf("CGO"),
        "合肥" to listOf("HFE"),
        "福州" to listOf("FOC"),
        "南昌" to listOf("KHN"),
        "贵阳" to listOf("KWE"),
        "南宁" to listOf("NNG"),
        "桂林" to listOf("KWL"),
        "太原" to listOf("TYN"),
        "石家庄" to listOf("SJW"),
        "兰州" to listOf("LHW"),
        "银川" to listOf("INC"),
        "西宁" to listOf("XNN"),
        "乌鲁木齐" to listOf("URC"),
        "拉萨" to listOf("LXA"),
        "呼和浩特" to listOf("HET"),
        "珠海" to listOf("ZUH"),
        "无锡" to listOf("WUX"),
        "宁波" to listOf("NGB"),
        "温州" to listOf("WNZ"),
        "泉州" to listOf("JJN"),
        "揭阳" to listOf("SWA"),
        "潮汕" to listOf("SWA"),
        "湛江" to listOf("ZHA"),
        "宜昌" to listOf("YIH"),
        "襄阳" to listOf("XFN"),
        "张家界" to listOf("DYG"),
        "丽江" to listOf("LJG"),
        "大理" to listOf("DLU"),
        "西双版纳" to listOf("JHG"),
        "腾冲" to listOf("TCZ"),
        "北海" to listOf("BHY"),
        "常州" to listOf("CZX"),
        "徐州" to listOf("XUZ"),
        "扬州" to listOf("YTY"),
        "南通" to listOf("NTG"),
        "烟台" to listOf("YNT"),
        "威海" to listOf("WEH"),
        "临沂" to listOf("LYI"),
        "济宁" to listOf("JNG"),
        "洛阳" to listOf("LYA"),
        "南阳" to listOf("NNY"),
        "恩施" to listOf("ENH"),
        "十堰" to listOf("WDS"),
        "荆州" to listOf("SHS"),
        "常德" to listOf("CGD"),
        "衡阳" to listOf("HNY"),
        "怀化" to listOf("HJJ"),
        "铜仁" to listOf("TEN"),
        "遵义" to listOf("ZYI"),
        "兴义" to listOf("ACX"),
        "六盘水" to listOf("LPF"),
        "毕节" to listOf("BFJ"),
        "万州" to listOf("WXN"),
        "巫山" to listOf("WSK"),
        "香港" to listOf("HKG"),
        "澳门" to listOf("MFM"),
        "台北" to listOf("TPE", "TSA"),
        "高雄" to listOf("KHH")
    )

    private val IATA = Regex("^[A-Z]{3}$")

    /** 返回候选三字码；无法识别时返回空列表，由调用方决定是否原样查询。 */
    fun resolve(input: String?): List<String> {
        val raw = input.orEmpty().trim()
        if (raw.isBlank()) return emptyList()

        val upper = raw.uppercase()
        if (IATA.matches(upper)) return listOf(upper)

        // 支持「重庆 (CKG)」「CKG 重庆」这类写法
        Regex("\\b([A-Za-z]{3})\\b").find(raw)?.groupValues?.getOrNull(1)?.uppercase()
            ?.takeIf { IATA.matches(it) }?.let { return listOf(it) }

        val city = raw
            .replace(Regex("市$"), "")
            .replace("机场", "")
            .replace(Regex("\\s+"), "")
        return CITY_TO_CODES[city].orEmpty()
    }

    /** 给输入框的辅助提示：命中多个机场时告诉用户实际会查哪些 */
    fun hint(input: String?): String {
        val codes = resolve(input)
        return when {
            codes.isEmpty() -> ""
            codes.size == 1 -> "将查询 ${codes[0]}"
            else -> "将查询 ${codes.joinToString(" / ")}"
        }
    }
}
