package com.example.my.data

import com.example.my.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** POI 搜索结果 */
data class PoiResult(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val typeName: String = "",
    val distance: Double = 0.0
)

/**
 * 选点页的地名解析结果。
 *
 * [name] 是可直接当地点名的短名字（景点 / 商场 / 道路名），
 * [region] 是「城市 · 区县」的行政描述，只用于给用户确认「选的是哪一片」。
 * 旧实现把两者混成一个「厦门市 · 思明区」，用户选完鼓浪屿，地点名却变成行政区名。
 */
data class PickedPlace(val name: String?, val region: String?)

/**
 * 高德地图地名 / POI 检索
 * 支持关键词搜索、周边搜索，自动按距离排序
 */
object AmapApi {

    /** 高德 Web 服务 Key（console.amap.com 申请，类型必须选「Web服务」）。
     *  仓库里不内置任何 Key：在本机 local.properties 写 `AMAP_KEY=xxx`（该文件不进仓库，
     *  由 Gradle 注入 BuildConfig），或直接在 App 设置页填自己的 Key（见 AppState.setAmapKey）。 */
    val KEY: String = BuildConfig.AMAP_KEY

    /** 用户在设置里填的 Key（见 AppState.setAmapKey）；留空则退回内置 Key */
    var userKey: String? = null

    private val key: String get() = userKey?.trim()?.takeIf { it.isNotBlank() } ?: KEY

    /**
     * 最近一次高德请求失败的原因（已转成中文）。
     * UI 在「结果为空」时读它——否则「Key 失效」和「真没这个地方」在界面上长得一模一样，
     * 用户只会以为是搜不到，永远查不出是服务挂了。
     */
    @Volatile
    var lastErrorHint: String? = null
        private set

    private fun noteError(info: String) {
        lastErrorHint = when {
            info.contains("USERKEY_PLAT_NOMATCH") ->
                "地图 Key 平台不匹配，需在设置里填「Web服务」类型的 Key"
            info.contains("INVALID_USER_KEY") -> "地图 Key 无效"
            info.contains("USERKEY_EXPIRED") -> "地图 Key 已过期"
            info.contains("OVER_LIMIT") -> "地图 Key 今日调用量已超限"
            info.isBlank() -> "地图服务无响应，请检查网络"
            else -> "地图服务返回：$info"
        }
    }

    /**
     * 兜底层抛出的异常也要记账。httpGet 在超时 / DNS 失败 / JSON 解析失败时是 error(...) 抛异常，
     * 被 runCatching 静默吞掉后 lastErrorHint 保持 null，界面就把「服务没连上」说成「没有这个地方」。
     * 已有更具体的错误（比如 Key 无效）时不覆盖。
     */
    private fun noteFailure(t: Throwable) {
        if (lastErrorHint != null) return
        lastErrorHint = when (t) {
            is SocketTimeoutException -> "网络超时，请检查网络连接"
            is IOException -> "网络异常，请检查网络连接"
            else -> "地图服务返回数据异常，请稍后重试"
        }
    }

    /** 兜底链上的每一级：失败记账后当空结果处理 */
    private suspend fun <T> fetch(
        block: suspend () -> List<T>
    ): List<T> = runCatching { block() }
        .onFailure { noteFailure(it) }
        .getOrDefault(emptyList())

    /**
     * 搜索POI
     * @param keyword 搜索关键词
     * @param city 城市名（可选，限制搜索范围）
     * @param currentLat 当前纬度（可选，用于距离排序）
     * @param currentLon 当前经度（可选，用于距离排序）
     * @param radius 搜索半径（米，默认50000米=50公里）
     * @param count 返回结果数量
     */
    suspend fun searchPoi(
        keyword: String,
        city: String = "",
        currentLat: Double? = null,
        currentLon: Double? = null,
        radius: Int = 50000,
        count: Int = 20,
        /**
         * city 只当「排序倾向」而不是硬限制。
         * 地图选点页需要这种语义：搜「人民公园」时优先给出当前城市的结果，
         * 但用户在远景地图上搜「鼓浪屿」时不能被城市卡死（会一条结果都没有）。
         */
        cityLimit: Boolean = true
    ): List<PoiResult> = withContext(Dispatchers.IO) {
        val q = keyword.trim()
        if (q.isEmpty()) return@withContext emptyList()

        val urlBuilder = StringBuilder("https://restapi.amap.com/v3/place/text?")
        urlBuilder.append("key=$key")
        urlBuilder.append("&keywords=${URLEncoder.encode(q, "UTF-8")}")
        urlBuilder.append("&types=190000|150000|140000|130000|120000|110000|100000|090000|080000|070000|060000|050000|040000|030000|020000|010000") // 全类别POI
        if (city.isNotBlank()) {
            urlBuilder.append("&city=${URLEncoder.encode(city, "UTF-8")}")
            if (cityLimit) urlBuilder.append("&citylimit=true")
            else urlBuilder.append("&citylimit=false") // 只当排序倾向，不硬卡城市
        } else {
            urlBuilder.append("&citylimit=false") // 不限制城市，全国搜索
        }
        if (currentLat != null && currentLon != null) {
            urlBuilder.append("&location=$currentLon,$currentLat")
            urlBuilder.append("&radius=$radius")
            urlBuilder.append("&sortrule=distance") // 按距离排序
        } else {
            urlBuilder.append("&sortrule=weight") // 按权重排序，重要地点优先
        }
        urlBuilder.append("&offset=$count")
        urlBuilder.append("&page=1")
        urlBuilder.append("&extensions=base")
        urlBuilder.append("&output=json")

        val url = urlBuilder.toString()
        // 这两个方法没有 runCatching 兜底：网络断了异常会一路抛到调用方（AI 工具链就是这样崩的），
        // 所以在这里就地吃掉，失败记进 lastErrorHint，界面按「服务不可用」提示。
        lastErrorHint = null
        val root = try {
            JSONObject(httpGet(url))
        } catch (t: Throwable) {
            noteFailure(t)
            return@withContext emptyList()
        }

        if (root.optInt("status") != 1) {
            noteError(root.optString("info"))
            return@withContext emptyList()
        }

        val arr = root.optJSONArray("pois") ?: return@withContext emptyList()

        val results = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").ifBlank { return@mapNotNull null }
            val location = o.optString("location")
            val parts = location.split(",")
            if (parts.size < 2) return@mapNotNull null
            val lon = parts[0].trim().toDoubleOrNull() ?: return@mapNotNull null
            val lat = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
            
            val distance = if (currentLat != null && currentLon != null) {
                calculateDistance(currentLat, currentLon, lat, lon)
            } else {
                0.0
            }
            
            PoiResult(
                name = name,
                address = o.optString("address").ifBlank { o.optString("type") },
                lat = lat,
                lon = lon,
                typeName = o.optString("type"),
                distance = distance
            )
        }
        
        // 如果没有传入当前位置，按重要性排序（大地名、知名地点优先）
        if (currentLat == null || currentLon == null) {
            // 升序：优先返回名称短的（通常是大地名）。
            // 之前写成 compareByDescending，名字最长的排最前，会出现「某某景区游客服务中心」压过「北京」
            results.sortedWith(compareBy<PoiResult> {
                it.name.length
            }.thenByDescending {
                it.address.contains("省") || it.address.contains("市")
            })
        } else {
            results
        }
    }

    /**
     * 输入提示（关键词联想）
     */
    suspend fun inputTips(
        keyword: String,
        city: String = "",
        currentLat: Double? = null,
        currentLon: Double? = null
    ): List<PoiResult> = withContext(Dispatchers.IO) {
        val q = keyword.trim()
        if (q.isEmpty()) return@withContext emptyList()

        val urlBuilder = StringBuilder("https://restapi.amap.com/v3/assistant/inputtips?")
        urlBuilder.append("key=$key")
        urlBuilder.append("&keywords=${URLEncoder.encode(q, "UTF-8")}")
        urlBuilder.append("&datatype=all") // POI + 公交 + 行政区全类型，避免漏掉行政区
        if (city.isNotBlank()) {
            urlBuilder.append("&city=${URLEncoder.encode(city, "UTF-8")}")
        }
        if (currentLat != null && currentLon != null) {
            urlBuilder.append("&location=$currentLon,$currentLat")
        }
        urlBuilder.append("&output=json")

        val url = urlBuilder.toString()
        // 这两个方法没有 runCatching 兜底：网络断了异常会一路抛到调用方（AI 工具链就是这样崩的），
        // 所以在这里就地吃掉，失败记进 lastErrorHint，界面按「服务不可用」提示。
        lastErrorHint = null
        val root = try {
            JSONObject(httpGet(url))
        } catch (t: Throwable) {
            noteFailure(t)
            return@withContext emptyList()
        }

        if (root.optInt("status") != 1) {
            noteError(root.optString("info"))
            return@withContext emptyList()
        }

        val arr = root.optJSONArray("tips") ?: return@withContext emptyList()

        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").ifBlank { return@mapNotNull null }
            val location = o.optString("location")
            if (location.isBlank()) return@mapNotNull null
            val parts = location.split(",")
            if (parts.size < 2) return@mapNotNull null
            val lon = parts[0].trim().toDoubleOrNull() ?: return@mapNotNull null
            val lat = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
            
            val distance = if (currentLat != null && currentLon != null) {
                calculateDistance(currentLat, currentLon, lat, lon)
            } else {
                0.0
            }

            // inputtips 的返回里没有 type 字段，只有 typecode。不补这一步 typeName 恒为空串，
            // 行政区划加权就永远触发不了。19 开头属于「地名地址信息」大类，行政区也在这一类里
            val typecode = o.optString("typecode")

            PoiResult(
                name = name,
                address = o.optString("district").ifBlank { o.optString("address") },
                lat = lat,
                lon = lon,
                typeName = o.optString("type").ifBlank {
                    if (typecode.startsWith("19")) "地名地址信息" else ""
                },
                distance = distance
            )
        }
    }

    /**
     * 统一地点搜索（地图/足迹页用）—— 三级级联：
     * 高德输入提示 → 高德POI全文检索 → OpenStreetMap Nominatim 全球兜底
     * 合并去重后按相关性排序，小地名/乡村/景区也能搜到
     */
    suspend fun searchPlaces(
        keyword: String,
        currentLat: Double? = null,
        currentLon: Double? = null,
        count: Int = 20
    ): List<PoiResult> = withContext(Dispatchers.IO) {
        val q = keyword.trim()
        if (q.isEmpty()) return@withContext emptyList()
        // 每次搜索先清空，保证 lastErrorHint 反映的是「本次」而不是上一次的失败
        lastErrorHint = null

        val all = LinkedHashMap<String, PoiResult>()
        fun merge(list: List<PoiResult>) {
            for (p in list) {
                val key = p.name + "@" + p.address
                if (key !in all) all[key] = p
            }
        }

        // 一级：输入提示（最快，带行政区联想）
        merge(fetch { inputTips(q, currentLat = currentLat, currentLon = currentLon) })
        // 二级：POI 全文检索（结果不足时补充）
        if (all.size < 5) {
            merge(fetch { searchPoi(q, currentLat = currentLat, currentLon = currentLon, count = count) })
        }
        // 三级：Open-Meteo 免 Key 兜底（国内可达；Nominatim 在国内基本连不通，所以排在它前面）
        if (all.size < 3) {
            merge(fetch { openMeteoSearch(q) })
        }
        // 四级：OSM Nominatim（只有海外网络才通，最后再试）
        if (all.isEmpty()) {
            merge(fetch { nominatimSearch(q) })
        }

        rankPlaces(q, all.values.toList(), currentLat, currentLon).take(count)
    }

    /**
     * OSM Nominatim 全球地名检索（开源免费，参考 github.com/osm-search/Nominatim）
     * 覆盖村庄、山区、景区等商业地图未收录地点
     */
    private suspend fun nominatimSearch(keyword: String, limit: Int = 8): List<PoiResult> =
        withContext(Dispatchers.IO) {
            val url = "https://nominatim.openstreetmap.org/search" +
                "?q=${URLEncoder.encode(keyword, "UTF-8")}" +
                "&format=jsonv2&limit=$limit&accept-language=zh&addressdetails=1"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "MYZujiApp/1.0 (android travel app)")
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (conn.responseCode !in 200..299) return@withContext emptyList()
                val arr = org.json.JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val lat = o.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
                    val lon = o.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
                    val display = o.optString("display_name")
                    val addrObj = o.optJSONObject("address")
                    // 取最短有意义名称：优先结构化地址，否则 display_name 第一段
                    val name = listOfNotNull(
                        addrObj?.optString("tourism")?.takeIf { it.isNotBlank() },
                        addrObj?.optString("village")?.takeIf { it.isNotBlank() },
                        addrObj?.optString("town")?.takeIf { it.isNotBlank() },
                        addrObj?.optString("city")?.takeIf { it.isNotBlank() },
                        addrObj?.optString("county")?.takeIf { it.isNotBlank() },
                        display.split(",").firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
                    ).firstOrNull() ?: return@mapNotNull null
                    val region = display.split(",").drop(1).take(2).joinToString(" ").trim()
                    PoiResult(
                        name = name,
                        address = region.ifBlank { o.optString("type") },
                        lat = lat,
                        lon = lon,
                        typeName = o.optString("category") + "/" + o.optString("type")
                    )
                }
            } finally {
                conn.disconnect()
            }
        }

    /**
     * Open-Meteo 地名检索：免费免 Key，国内可达（和天气服务同源，已验证可用）。
     * 覆盖主要城市与部分区县，但县级小地名和 POI 不全，只能当高德失效时的保底，
     * 不能指望它搜到「秀山县」这种——所以真正的解法还是让用户在设置里填一个有效的高德 Key。
     */
    private suspend fun openMeteoSearch(keyword: String, limit: Int = 10): List<PoiResult> =
        withContext(Dispatchers.IO) {
            val q = keyword.trim()
            if (q.isEmpty()) return@withContext emptyList()
            val url = "https://geocoding-api.open-meteo.com/v1/search" +
                "?name=${URLEncoder.encode(q, "UTF-8")}" +
                "&count=$limit&language=zh&format=json"
            runCatching {
                val root = JSONObject(httpGet(url))
                val arr = root.optJSONArray("results") ?: return@runCatching emptyList()
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val lat = o.optDouble("latitude").takeIf { !it.isNaN() } ?: return@mapNotNull null
                    val lon = o.optDouble("longitude").takeIf { !it.isNaN() } ?: return@mapNotNull null
                    val name = o.optString("name").ifBlank { return@mapNotNull null }
                    val admin1 = o.optString("admin1")
                    val admin2 = o.optString("admin2")
                    val region = listOf(admin1, admin2)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(" ")
                        .ifBlank { o.optString("country") }
                    PoiResult(
                        name = name,
                        address = region,
                        lat = lat,
                        lon = lon,
                        typeName = o.optString("feature_code")
                    )
                }
            }.onFailure { noteFailure(it) }.getOrDefault(emptyList())
        }

    /** 是否行政区划类条目（省/市/区/县本体），这类结果要排在同名 POI 之前 */
    private fun isAdminPlace(typeName: String): Boolean =
        typeName.contains("区县级地名") || typeName.contains("地级市") ||
            typeName.contains("行政区划") || typeName.contains("省级地名") ||
            // inputtips 只有 typecode，转换后只能拿到「地名地址信息」这层粒度
            typeName.contains("地名地址信息")

    /**
     * 用户明显在找行政区才加权：关键词以 省/市/区/县/镇/乡 结尾。
     * 否则搜「黄山」搜出来的是景区，不该被「黄山市」顶掉。
     */
    private fun isAdminQuery(q: String): Boolean =
        q.endsWith("省") || q.endsWith("市") || q.endsWith("区") ||
            q.endsWith("县") || q.endsWith("镇") || q.endsWith("乡")

    /**
     * 行政区划加权。必须小于前缀匹配的 +60：地图页是「看地点」场景，
     * 搜「西湖」时不能让「西湖区」压过「西湖风景名胜区」。
     */
    private const val ADMIN_PLACE_BONUS = 30

    /** 相关性排序：精确匹配 > 前缀匹配 > 包含匹配；同名时近者优先 */
    private fun rankPlaces(
        q: String,
        list: List<PoiResult>,
        currentLat: Double?,
        currentLon: Double?
    ): List<PoiResult> {
        fun score(p: PoiResult): Int {
            var s = 0
            if (p.name == q) s += 100
            else if (p.name.startsWith(q)) s += 60
            else if (p.name.contains(q)) s += 40
            if (p.address.contains(q)) s += 15
            if (p.address.contains("省") || p.address.contains("市")) s += 5
            // 行政区划条目优先：搜「秀山县」时，「秀山土家族苗族自治县」要压过「秀山县第一中学」。
            // 只在关键词本身就带区划后缀时加权，否则会把用户想看的景区顶掉（搜「黄山」出来黄山市）
            if (isAdminQuery(q) && isAdminPlace(p.typeName)) s += ADMIN_PLACE_BONUS
            return s
        }
        return list.sortedWith(
            compareByDescending<PoiResult> { score(it) }
                .thenBy { if (currentLat != null && currentLon != null) it.distance else 0.0 }
        )
    }

    /**
     * 搜索城市/地区（用于天气模块）—— 三级级联：
     * 行政区查询（不限级别：省/市/区/县/乡镇都能匹配）→ 输入提示 → Nominatim 兜底
     */
    suspend fun searchCity(
        keyword: String,
        currentLat: Double? = null,
        currentLon: Double? = null
    ): List<CityHit> = withContext(Dispatchers.IO) {
        val q = keyword.trim()
        if (q.isEmpty()) return@withContext emptyList()
        lastErrorHint = null

        val all = LinkedHashMap<String, CityHit>()
        fun merge(list: List<CityHit>) {
            for (c in list) {
                val key = c.name + "@" + c.region
                if (key !in all) all[key] = c
            }
        }

        // 一级：行政区查询（去掉 level=city 限制，县/区/乡镇也能搜到）
        runCatching {
            val url = "https://restapi.amap.com/v3/config/district?" +
                "key=$key&keywords=${URLEncoder.encode(q, "UTF-8")}" +
                "&subdistrict=0&output=json&showbiz=false"
            val root = JSONObject(httpGet(url))
            if (root.optInt("status") != 1) {
                noteError(root.optString("info"))
            } else {
                val arr = root.optJSONArray("districts")
                if (arr != null) merge((0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val name = o.optString("name").ifBlank { return@mapNotNull null }
                    val parts = o.optString("center").split(",")
                    if (parts.size < 2) return@mapNotNull null
                    val lon = parts[0].trim().toDoubleOrNull() ?: return@mapNotNull null
                    val lat = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
                    // 这一级就是行政区本体，必须带上类型标记，否则反而永远拿不到行政区划加权
                    CityHit(
                        name,
                        o.optString("pname").ifBlank { o.optString("province") },
                        lat,
                        lon,
                        typeName = "行政区划"
                    )
                })
            }
        }.onFailure { noteFailure(it) }

        // 二级：输入提示（覆盖景区/商圈等非行政区地名）
        if (all.size < 5) {
            merge(fetch {
                inputTips(q, currentLat = currentLat, currentLon = currentLon)
                    .map { CityHit(it.name, it.address, it.lat, it.lon) }
            })
        }
        // 三级：POI 全文检索。必需——行政区接口只认官方全称，
        // 搜「秀山县」会 0 条（官方名是「秀山土家族苗族自治县」），而 POI 里收录了「区县级地名」这类条目。
        if (all.size < 5) {
            merge(fetch {
                searchPoi(q, currentLat = currentLat, currentLon = currentLon)
                    .map { CityHit(it.name, it.address, it.lat, it.lon, it.typeName) }
            })
        }

        // 四级：Open-Meteo 免 Key 兜底（国内可达，优先于 Nominatim）
        if (all.size < 3) {
            merge(fetch { openMeteoSearch(q).map { CityHit(it.name, it.address, it.lat, it.lon) } })
        }
        // 五级：Nominatim（只有海外网络才通）
        if (all.isEmpty()) {
            merge(fetch { nominatimSearch(q).map { CityHit(it.name, it.address, it.lat, it.lon) } })
        }

        val results = all.values.toList()

        // 排序：精确/前缀匹配优先；有当前位置时按距离，否则直辖市/省会优先
        fun score(c: CityHit): Int {
            var s = 0
            if (c.name == q) s += 100
            else if (c.name.startsWith(q)) s += 60
            else if (c.name.contains(q)) s += 40
            if (c.name in listOf("北京", "上海", "天津", "重庆")) s += 8
            else if (c.name.endsWith("市") || c.name.endsWith("省")) s += 4
            // 搜「秀山县」时官方全称是「秀山土家族苗族自治县」（不含"山县"子串，匹配不上），
            // 靠行政区划类型标记把它顶上来，否则排第一的会是「秀山县第一中学」
            if (isAdminQuery(q) && isAdminPlace(c.typeName)) s += ADMIN_PLACE_BONUS
            return s
        }
        results.sortedWith(
            compareByDescending<CityHit> { score(it) }
                .thenBy {
                    if (currentLat != null && currentLon != null)
                        calculateDistance(currentLat, currentLon, it.lat, it.lon)
                    else 0.0
                }
        ).take(15)
    }

    /**
     * 选点页专用逆地理：一次拿到「地点名」和「行政区域」。
     *
     * 为什么不能直接用 [reverseGeocode]：它只会回「厦门市 · 思明区」这种行政区，
     * 而用户在地图上点的是鼓浪屿——拿行政区当地点名，最后足迹名字全是「XX市 · XX区」。
     *
     * 取名顺序：最近的 POI（景点/商场/车站）→ 道路名 → 城市·区县。
     * POI 只取 300 米内最近的 5 个里距离最小的一个，避免抓到远处的同名连锁店。
     */
    suspend fun reversePick(lat: Double, lon: Double): PickedPlace? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://restapi.amap.com/v3/geocode/regeo?" +
                "key=$key&location=${lon},${lat}&extensions=all&radius=300&output=json"
            val root = JSONObject(httpGet(url))
            if (root.optInt("status") != 1) {
                noteError(root.optString("info"))
                return@runCatching null
            }
            val re = root.optJSONObject("regeocode") ?: return@runCatching null
            val comp = re.optJSONObject("addressComponent")
            val city = comp?.optString("city")?.takeIf { it.isNotBlank() && it != "[]" }
            val district = comp?.optString("district")?.takeIf { it.isNotBlank() && it != "[]" }
            val province = comp?.optString("province")?.takeIf { it.isNotBlank() && it != "[]" }
            val region = when {
                city != null && district != null -> "$city · $district"
                city != null -> city
                province != null && district != null -> "$province · $district"
                district != null -> district
                else -> null
            }
            val pois = re.optJSONArray("pois")
            var bestName: String? = null
            var bestDist = Double.MAX_VALUE
            if (pois != null) {
                val n = if (pois.length() < 6) pois.length() else 6
                for (i in 0 until n) {
                    val p = pois.optJSONObject(i) ?: continue
                    val nm = p.optString("name").takeIf { it.isNotBlank() } ?: continue
                    val d = p.optString("distance").toDoubleOrNull() ?: 9999.0
                    if (d < bestDist) {
                        bestDist = d
                        bestName = nm
                    }
                }
            }
            val street = comp?.optJSONObject("streetNumber")
                ?.optString("street")?.takeIf { it.isNotBlank() }
            PickedPlace(bestName ?: street, region)
        }.onFailure { noteFailure(it) }.getOrNull()
    }

    /**
     * 逆地理编码：经纬度 → 地点名（取区县/ city / province）。
     * 用于照片 EXIF GPS 自动回填地点，失败返回 null。
     */
    suspend fun reverseGeocode(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://restapi.amap.com/v3/geocode/regeo?" +
                "key=$key&location=${lon},${lat}&extensions=base&output=json"
            val root = JSONObject(httpGet(url))
            if (root.optInt("status") != 1) {
                noteError(root.optString("info"))
                return@runCatching null
            }
            val comp = root.optJSONObject("regeocode")?.optJSONObject("addressComponent")
                ?: return@runCatching null
            val city = comp.optString("city").takeIf { it.isNotBlank() && it != "[]" }
            val district = comp.optString("district").takeIf { it.isNotBlank() && it != "[]" }
            val province = comp.optString("province").takeIf { it.isNotBlank() && it != "[]" }
            // 优先「城市 · 区县」，直辖市 city 为空时退化为「省 · 区县」
            when {
                city != null && district != null -> "$city · $district"
                city != null -> city
                province != null && district != null -> "$province · $district"
                district != null -> district
                else -> null
            }
        }.onFailure { noteFailure(it) }.getOrNull()
    }

    /**
     * 计算两点之间的距离（米）
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // 地球半径（米）
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    /**
     * 连接失败后的退避窗口。
     * 手机没网的时候，一次页面刷新会并发发出好几个请求，每个都干等 8 秒超时，
     * 日志里就是同一秒连着五条 ConnectException。失败一次后 15 秒内直接快速失败，
     * 让界面立刻走「地图服务不可用」分支，而不是卡着不动。
     */
    @Volatile
    private var networkBackoffUntil = 0L

    private fun httpGet(url: String): String {
        if (System.currentTimeMillis() < networkBackoffUntil) {
            throw IOException("地图服务暂不可用（网络失败冷却中）")
        }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            noteFailure(e)
            networkBackoffUntil = System.currentTimeMillis() + 15_000L
            throw e
        } finally {
            conn.disconnect()
        }
    }
}
