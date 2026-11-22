package com.example.my.data.ai

import com.example.my.data.CommandIntent
import com.example.my.data.AiPresets
import com.example.my.data.Locator
import android.net.Uri
import com.example.my.data.music.LocalSong
import com.example.my.data.music.MusicAi
import com.example.my.data.music.MusicPlayer
import com.example.my.data.music.QueueSource
import com.example.my.data.music.SongFetch
import com.example.my.data.music.SongMeta
import com.example.my.data.music.SongSearch
import com.example.my.data.music.WebMusicCache
import com.example.my.data.ticket.FlightTicketApi
import com.example.my.data.ticket.TicketDates
import com.example.my.data.ticket.TrainTicketApi
import com.example.my.state.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * 能力注册表：AI 能调度的一切，都在这里登记。
 *
 * 设计原则：
 * 1. 一处登记，四处复用 —— 模型工具 schema、执行分发、确认分级、覆盖率审计。
 * 2. 保留 `AppState.applyCommand` 作为底层执行器，P0 不改旧行为，只在外面套统一网关；
 *    P1 再逐个收敛成直接调用，降低迁移风险。
 * 3. 破坏性能力先登记但 [Capability.gated] = true，不导出给模型、不开放执行，
 *    等二次确认 UI 做完再放开 —— 绝不因为「接得全」就把删除权限交出去。
 */
object CapabilityRegistry {

    /** 把「07:40」这类发车时间归到用户说的时段里，供一句话找票按偏好筛选。 */
    private fun matchesTimeHint(departTime: String, hint: String): Boolean {
        if (hint.isBlank()) return true
        val hour = departTime.substringBefore(":").toIntOrNull() ?: return true
        return when (hint) {
            "凌晨" -> hour in 0..5
            "早上" -> hour in 5..9
            "上午" -> hour in 8..12
            "中午" -> hour in 11..13
            "下午" -> hour in 13..18
            "傍晚" -> hour in 17..19
            "晚上", "夜间" -> hour in 18..24
            else -> true
        }
    }

    private val items: List<Capability> = mutableListOf<Capability>().apply {
        // ------------------------------------------------------------ 只读查询
        add(
            Capability(
                id = "query_footprints",
                title = "查足迹",
                desc = "读取用户在本 App 记录的足迹：城市、地点、日期、备注。可按城市筛选、限制条数。",
                group = "查询", risk = CapRisk.READ,
                params = listOf(
                    CapParam("city", desc = "城市名，留空表示不限", maxLen = 40),
                    CapParam("limit", type = CapType.INT, desc = "最多返回几条，默认 8", maxLen = 4)
                ),
                backedBy = setOf("footprints"),
                execute = {
                    val city = str("city")
                    val limit = int("limit", 8).coerceIn(1, 30)
                    val list = state.footprints
                        .filter { city.isBlank() || it.city.contains(city, true) }
                        .takeLast(limit)
                    val text = if (list.isEmpty()) "没有匹配的足迹"
                    else list.joinToString("；") { "${it.city}·${it.name}（${it.date}）" }
                    CapResult(true, "已读取 ${list.size} 条足迹", text)
                }
            )
        )
        add(
            Capability(
                id = "query_guides",
                title = "查攻略",
                desc = "按关键词检索 App 内已有攻略的标题、城市、类型、标签。",
                group = "查询", risk = CapRisk.READ,
                params = listOf(CapParam("keyword", desc = "城市或关键词，留空返回最近攻略", maxLen = 40)),
                backedBy = setOf("guides"),
                execute = {
                    val key = str("keyword")
                    val list = state.guides
                        .filter {
                            key.isBlank() || it.title.contains(key, true) || it.city.contains(key, true) ||
                                it.type.contains(key, true) || it.tags.any { t -> t.contains(key, true) }
                        }
                        .take(10)
                    CapResult(
                        true, "找到 ${list.size} 篇攻略",
                        if (list.isEmpty()) "没有匹配攻略" else list.joinToString("；") { "《${it.title}》(${it.city})" }
                    )
                }
            )
        )
        add(
            Capability(
                id = "query_trips",
                title = "查行程",
                desc = "列出用户的行程计划：名称、目的地、出发日期、天数、是否归档。",
                group = "查询", risk = CapRisk.READ,
                backedBy = setOf("tripPlans"),
                execute = {
                    val list = state.tripPlans.take(10)
                    CapResult(
                        true, "共 ${state.tripPlans.size} 个行程",
                        if (list.isEmpty()) "还没有行程计划"
                        else list.joinToString("；") {
                            "《${it.name}》${it.destination} ${it.startDate} ${it.days}天" +
                                if (it.archived) "（已归档）" else ""
                        }
                    )
                }
            )
        )
        add(
            Capability(
                id = "query_routes",
                title = "查路线",
                desc = "列出用户保存的旅行路线标题与数量。",
                group = "查询", risk = CapRisk.READ,
                backedBy = setOf("routes"),
                execute = {
                    val list = state.routes.take(10)
                    CapResult(
                        true, "共 ${state.routes.size} 条路线",
                        if (list.isEmpty()) "还没有保存的路线" else list.joinToString("；") { "《${it.title}》" }
                    )
                }
            )
        )
        add(
            Capability(
                id = "query_notifications",
                title = "查通知",
                desc = "查看 App 内通知条数，不展开具体内容。",
                group = "查询", risk = CapRisk.READ,
                backedBy = setOf("notifications"),
                execute = { CapResult(true, "通知 ${state.notifications.size} 条", "通知总数：${state.notifications.size}") }
            )
        )
        add(
            Capability(
                id = "query_weather",
                title = "查天气缓存",
                desc = "读取 App 当前天气城市与已缓存的实况（不联网，可能不是最新）。",
                group = "查询", risk = CapRisk.READ,
                backedBy = setOf("weatherCity", "weatherNow"),
                execute = {
                    val w = state.weatherNow
                    CapResult(
                        true, "天气城市：${state.weatherCity}",
                        if (w == null) "当前没有天气缓存，只有城市：${state.weatherCity}"
                        else "${w.city} ${w.temp}℃ ${w.desc}，更新于 ${w.updatedAt}"
                    )
                }
            )
        )
        add(
            Capability(
                id = "query_settings",
                title = "查设置",
                desc = "读取用户的非敏感偏好：昵称、默认公开、通知、分享、主题、语言、当前对话模型。",
                group = "查询", risk = CapRisk.READ,
                backedBy = setOf("settings", "themeMode", "language", "aiCapabilities"),
                execute = {
                    val s = state.settings
                    CapResult(
                        true, "已读取偏好设置",
                        "昵称=${s.nickname}；默认公开=${s.defaultPublic}；通知=${s.notifEnabled}；" +
                            "分享=${s.shareEnabled}；主题模式=${state.themeMode}；界面语言=${state.language}；" +
                            "当前对话模型=${state.activeModelName}"
                    )
                }
            )
        )
        add(
            Capability(
                id = "query_location",
                title = "查定位可用性",
                desc = "判断设备是否有可用的最近定位。只返回有无与精度等级，不返回精确坐标。",
                group = "查询", risk = CapRisk.READ,
                backedBy = setOf("lastKnown"),
                execute = {
                    // 隐私：精确坐标绝不进 system / 不发云端，只给模型一个可用性结论
                    val fix = runCatching { Locator.lastKnown(app) }.getOrNull()
                    CapResult(
                        true, if (fix == null) "暂无定位" else "有定位",
                        if (fix == null) "用户没有可用定位，涉及「附近/从这里」的问题要先反问城市"
                        else "有最近定位，精度约 ${fix.accuracyM.toInt()} 米（坐标不提供）"
                    )
                }
            )
        )

        // ------------------------------------------------------------ 票务 / 音乐（真实能力，不是网页入口）
        add(
            Capability(
                id = "query_train_tickets",
                title = "查火车票余票",
                desc = "查询真实火车余票（12306 只读数据）。必须传出发地、到达地；date 用 yyyy-MM-dd，" +
                    "用户说今天/明天/后天时先按当前日期换算。用户提「上午/下午/晚上」时把时段放进 time_hint。" +
                    "返回车次、时刻、历时和各席别余票。",
                group = "票务", risk = CapRisk.READ, network = CapNetwork.REQUIRES_NETWORK,
                params = listOf(
                    CapParam("from_city", desc = "出发城市，例如 重庆", required = true, maxLen = 24),
                    CapParam("to_city", desc = "到达城市，例如 秀山", required = true, maxLen = 24),
                    CapParam("date", desc = "乘车日期 yyyy-MM-dd", maxLen = 12),
                    CapParam("time_hint", desc = "时段偏好：上午/下午/晚上等，可留空", maxLen = 8)
                ),
                backedBy = setOf("TrainTicketApi.query", "TicketDates"),
                execute = exec@ {
                    val from = str("from_city").trim()
                    val to = str("to_city").trim()
                    val hint = str("time_hint").trim()
                    val date = TicketDates.resolve(str("date"))
                    try {
                        val r = withTimeoutOrNull(30_000L) { TrainTicketApi.query(from, to, date) }
                            ?: return@exec CapResult(false, "12306 查询超时，请稍后重试")
                        val matched = if (hint.isBlank()) r.trains
                        else r.trains.filter { matchesTimeHint(it.departTime, hint) }
                        val shown = matched.ifEmpty { r.trains }
                        val available = shown.filter { it.available }
                        val picked = available.ifEmpty { shown }.take(12)
                        val data = buildString {
                            appendLine("${r.from}→${r.to} ${r.date}：共 ${r.total} 趟，其中有票 ${available.size} 趟")
                            if (matched.isEmpty() && hint.isNotBlank()) {
                                appendLine("没有 $hint 出发的车次，以下为全部车次")
                            } else if (hint.isNotBlank()) {
                                appendLine("已按「$hint」筛选")
                            }
                            if (picked.isEmpty()) appendLine("没有可展示的车次")
                            else picked.forEach { appendLine(it.copyLine(r.date)) }
                            if (r.note.isNotBlank()) appendLine("说明：${r.note}")
                            append("只读余票，不提供购票；购票请前往 12306 官方渠道。")
                        }
                        CapResult(
                            true,
                            if (picked.isEmpty()) "没有查到可售车次" else "查到 ${r.total} 趟车，${available.size} 趟有票",
                            data
                        )
                    } catch (e: Exception) {
                        CapResult(false, "火车票查询失败：${e.message?.take(80) ?: "未知错误"}")
                    }
                }
            )
        )
        add(
            Capability(
                id = "query_flights",
                title = "查机票比价",
                desc = "查询真实航班价格（免费实时比价）。出发地/目的地可传中文城市名或三字码；" +
                    "date 用 yyyy-MM-dd，用户说今天/明天/后天时先换算。用户提预算就传 budget，提上午/下午就传 time_hint。" +
                    "返回航班号、航司、时刻、价格、直飞或经停。",
                group = "票务", risk = CapRisk.READ, network = CapNetwork.REQUIRES_NETWORK,
                params = listOf(
                    CapParam("from_city", desc = "出发城市或机场三字码", required = true, maxLen = 24),
                    CapParam("to_city", desc = "到达城市或机场三字码", required = true, maxLen = 24),
                    CapParam("date", desc = "出发日期 yyyy-MM-dd", maxLen = 12),
                    CapParam("budget", type = CapType.INT, desc = "预算上限（元），可留空", maxLen = 8),
                    CapParam("time_hint", desc = "时段偏好：上午/下午/晚上等，可留空", maxLen = 8)
                ),
                backedBy = setOf("FlightTicketApi.search", "TicketDates"),
                execute = exec@ {
                    val from = str("from_city").trim()
                    val to = str("to_city").trim()
                    val date = TicketDates.resolve(str("date"))
                    val budget = int("budget", 0).takeIf { it > 0 }
                    val hint = str("time_hint").trim()
                    try {
                        val r = withTimeoutOrNull(35_000L) { FlightTicketApi.search(from, to, date) }
                            ?: return@exec CapResult(false, "机票查询超时，请稍后重试")
                        val filtered = r.offers.filter { offer ->
                            (budget == null || (offer.price ?: Double.MAX_VALUE) <= budget) &&
                                matchesTimeHint(offer.departTime, hint)
                        }
                        val shown = filtered.ifEmpty { r.offers }
                        val data = buildString {
                            appendLine("${from}→${to} ${date}：${r.offers.size} 个报价，按价格从低到高")
                            if (budget != null || hint.isNotBlank()) {
                                appendLine(
                                    "已按" +
                                        listOfNotNull(
                                            budget?.let { "预算≤${it}元" },
                                            hint.takeIf { it.isNotBlank() }
                                        ).joinToString("、") +
                                        "筛选，符合 ${filtered.size} 个"
                                )
                            }
                            shown.take(10).forEach {
                                appendLine(
                                    "${it.flightNo} ${it.airline} ${it.departTime}-${it.arriveTime} " +
                                        "${it.priceText} ${it.stopsText} ${it.baggageText}"
                                )
                            }
                            append("说明：${r.note}；只做比价展示，不提供下单。")
                        }
                        CapResult(true, "查到 ${r.offers.size} 个航班报价，符合 ${shown.size} 个", data)
                    } catch (e: Exception) {
                        CapResult(false, "机票查询失败：${e.message?.take(80) ?: "未知错误"}")
                    }
                }
            )
        )
        add(
            Capability(
                id = "find_music_by_description",
                title = "按描述找歌",
                desc = "用户描述场景、心情，或说「想听像某首歌」时用。AI 只负责提议候选，" +
                    "每首都会在 App 真实曲库里核对，只返回核对到的歌；只找歌，不播放。",
                group = "音乐", risk = CapRisk.READ, network = CapNetwork.OPTIONAL,
                params = listOf(
                    CapParam("description", desc = "场景/心情/参考歌曲，例如「下班通勤想听点轻快的」", required = true, maxLen = 120)
                ),
                backedBy = setOf("MusicAi.recommend", "SongSearch.search"),
                execute = exec@ {
                    val desc = str("description").trim()
                    if (desc.isBlank()) return@exec CapResult(false, "缺少想听什么的描述")
                    val endpoint = state.currentEndpoint.takeIf { it.ready } ?: AiPresets.builtin
                    val plan = withTimeoutOrNull(50_000L) { MusicAi.recommend(endpoint, desc) }
                        ?: return@exec CapResult(false, "AI 找歌超时，请稍后再试")
                    val data = buildString {
                        appendLine(plan.summary)
                        if (plan.interpretation.isNotBlank()) appendLine("理解：${plan.interpretation}")
                        plan.tracks.forEach {
                            appendLine(
                                "${it.title} - ${it.artist}" +
                                    if (it.reason.isBlank()) "" else "｜${it.reason}"
                            )
                        }
                    }.trim()
                    CapResult(
                        plan.tracks.isNotEmpty(),
                        plan.summary,
                        data.ifBlank { "曲库里没有核对到候选，应如实告诉用户换个说法" }
                    )
                }
            )
        )
        add(
            Capability(
                id = "make_mood_playlist",
                title = "AI 情绪歌单",
                desc = "用户描述场景/心情并想要一份歌单时用（例如「来点适合海边傍晚的歌」「做个开车听的歌单」）。" +
                    "AI 提议候选并逐首核对真实曲库，核对到的组成队列直接开始播放；核对不到会如实说明。",
                group = "音乐", risk = CapRisk.WRITE, network = CapNetwork.REQUIRES_NETWORK, reversible = true,
                params = listOf(
                    CapParam("description", desc = "场景/心情/风格描述", required = true, maxLen = 120),
                    CapParam("count", desc = "想要几首（3-10，默认 6）", required = false)
                ),
                backedBy = setOf("MusicAi.playlist", "MusicPlayer.addPendingSongs", "MusicPlayer.playFrom"),
                execute = exec@ {
                    val desc = str("description").trim()
                    if (desc.isBlank()) return@exec CapResult(false, "缺少歌单的描述")
                    val want = str("count").toIntOrNull()?.coerceIn(3, 10) ?: 6
                    val endpoint = state.currentEndpoint.takeIf { it.ready } ?: AiPresets.builtin
                    val plan = withTimeoutOrNull(90_000L) { MusicAi.playlist(endpoint, desc, want) }
                        ?: return@exec CapResult(false, "AI 组歌单超时，请稍后再试")
                    if (plan.tracks.isEmpty()) {
                        return@exec CapResult(false, plan.summary, "曲库里没有核对到候选，应如实告诉用户换个说法")
                    }
                    // 先落成「待获取」占位：点击播放时才按需搜源下载，避免一次下 6 首歌
                    val base = -(System.currentTimeMillis() / 10)
                    val items = plan.tracks.mapIndexed { i, t ->
                        LocalSong(
                            id = base - i,
                            title = t.title,
                            artist = t.artist,
                            album = "",
                            durationMs = 0L,
                            uri = Uri.EMPTY,
                            pendingSearch = true
                        )
                    }
                    withContext(Dispatchers.Main) {
                        MusicPlayer.addPendingSongs(items)
                        MusicPlayer.playFrom(items, items.first(), QueueSource.PLAYLIST)
                    }
                    CapResult(
                        true,
                        "${plan.summary}，已开始播放",
                        buildString {
                            appendLine("interpretation=${plan.interpretation}")
                            plan.tracks.forEach { appendLine("${it.title} - ${it.artist}｜${it.reason}") }
                            append("queueSize=${items.size}")
                        }.trim()
                    )
                }
            )
        )
        add(
            Capability(
                id = "search_music",
                title = "搜歌",
                desc = "在 App 的真实曲库里搜歌，返回歌名、歌手和可播放来源。只搜不播放，适合用户问「有没有这首歌」。",
                group = "音乐", risk = CapRisk.READ, network = CapNetwork.REQUIRES_NETWORK,
                params = listOf(CapParam("query", desc = "歌名，可带歌手", required = true, maxLen = 80)),
                backedBy = setOf("SongSearch.search"),
                execute = {
                    val q = str("query").trim()
                    val hits = withTimeoutOrNull(18_000L) { SongSearch.search(q, 10) }.orEmpty()
                    if (hits.isEmpty()) {
                        CapResult(false, "曲库里没有搜到「$q」", "没有匹配结果，应如实告诉用户没搜到，不要编造歌词或音源")
                    } else {
                        CapResult(
                            true, "找到 ${hits.size} 首相关歌曲",
                            hits.take(8).joinToString("\n") { "${it.title} - ${it.artist}" }
                        )
                    }
                }
            )
        )
        add(
            Capability(
                id = "play_music",
                title = "播放歌曲",
                desc = "用户明确说「播放/放一下/听某首歌」时，在真实曲库里搜索、取音源并直接开始播放。只传歌名和歌手，不要传描述。",
                group = "音乐", risk = CapRisk.WRITE, network = CapNetwork.REQUIRES_NETWORK,
                reversible = true,
                params = listOf(
                    CapParam("title", desc = "歌名", required = true, maxLen = 80),
                    CapParam("artist", desc = "歌手，可留空", maxLen = 40)
                ),
                backedBy = setOf("SongSearch.search", "SongSearch.fetch", "MusicPlayer.play"),
                execute = exec@ {
                    val title = str("title").trim()
                    val artist = str("artist").trim()
                    if (title.isBlank()) return@exec CapResult(false, "缺少歌名")
                    val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
                    val hits = withTimeoutOrNull(18_000L) { SongSearch.search(query, 10) }.orEmpty()
                    val pick = MusicAi.pickMatch(title, artist, hits) ?: hits.firstOrNull()
                        ?: return@exec CapResult(false, "曲库里没有找到《$title》")
                    val fetched = withTimeoutOrNull(28_000L) { SongSearch.fetch(pick) }
                    val audio = (fetched as? SongFetch.Ok)?.audio
                        ?: return@exec CapResult(false, "找到了《${pick.title}》，但音源获取失败，可以到音乐页手动重试")
                    val song = withTimeoutOrNull(30_000L) {
                        WebMusicCache.downloadAwait(
                            context = app,
                            url = audio.url,
                            meta = SongMeta(audio.title, audio.artist),
                            referer = audio.referer,
                            cookie = audio.cookie,
                            userAgent = audio.userAgent
                        )
                    }?.getOrNull()
                        ?: return@exec CapResult(false, "《${pick.title}》下载失败，可以到音乐页手动重试")
                    withContext(Dispatchers.Main) {
                        WebMusicCache.saveLyrics(app, song, audio.lrc)
                        MusicPlayer.addCachedWebSong(song)
                        MusicPlayer.play(song)
                    }
                    CapResult(
                        true, "正在播放《${song.title}》 - ${song.artist}",
                        "playing=${song.title}|${song.artist}；歌词=${if (audio.lrc.isBlank()) "无" else "已写入"}"
                    )
                }
            )
        )
        add(
            Capability(
                id = "control_music",
                title = "控制播放",
                desc = "控制当前播放器：暂停、继续、下一首、上一首。只改播放状态，不动任何文件。",
                group = "音乐", risk = CapRisk.WRITE, reversible = true,
                params = listOf(
                    CapParam(
                        "action", desc = "操作", required = true,
                        values = listOf("pause", "resume", "next", "previous")
                    )
                ),
                backedBy = setOf("MusicPlayer.toggle", "MusicPlayer.next", "MusicPlayer.prev"),
                execute = {
                    val action = str("action").trim().lowercase()
                    val result = withContext(Dispatchers.Main) {
                        when (action) {
                            "pause" -> {
                                if (MusicPlayer.isPlaying) MusicPlayer.pause()
                                "已暂停" to MusicPlayer.current
                            }
                            "resume" -> {
                                if (MusicPlayer.current == null) return@withContext "没有正在播放的歌" to null
                                if (!MusicPlayer.isPlaying) MusicPlayer.toggle()
                                "已继续播放" to MusicPlayer.current
                            }
                            "next" -> {
                                if (MusicPlayer.currentQueue.isEmpty()) return@withContext "播放队列是空的" to null
                                MusicPlayer.next()
                                "已切到下一首" to MusicPlayer.current
                            }
                            "previous" -> {
                                if (MusicPlayer.currentQueue.isEmpty()) return@withContext "播放队列是空的" to null
                                MusicPlayer.prev()
                                "已切到上一首" to MusicPlayer.current
                            }
                            else -> "不支持的操作：$action" to null
                        }
                    }
                    val (message, song) = result
                    CapResult(
                        song != null,
                        if (song != null) "$message：《${song.title}》 - ${song.artist}" else message,
                        song?.let { "current=${it.title}|${it.artist}" }.orEmpty()
                    )
                }
            )
        )

        // ------------------------------------------------------------ 页面调度
        add(
            cmd(
                id = "open_screen", title = "打开页面",
                desc = "打开 App 内指定页面或切换底部标签。",
                kind = "open", group = "调度",
                params = listOf(
                    CapParam(
                        "target", desc = "目标页面", required = true,
                        values = listOf(
                            "settings", "weather", "notifications", "messages", "guide_create",
                            "my_plans", "year_report", "map", "memory", "profile", "discover",
                            "ticket_zone", "music", "course", "news", "stock", "trade", "creative",
                            "travelers", "web_search", "ai", "upload", "offline", "trips"
                        )
                    )
                )
            ) { CommandIntent("open", it.str("target"), "", "打开${it.str("target")}") }
        )
        add(
            cmd(
                id = "open_settings_section", title = "打开设置项",
                desc = "直接把设置页定位到某个分类或某个弹层：外观、语言、触感、备份恢复、回收站、" +
                    "本地模型、语音库、API 模型、隐私与安全等。用户说「打开设置里的XX」时用它。",
                kind = "settings_page", group = "设置",
                params = listOf(
                    CapParam(
                        "target", desc = "设置项", required = true,
                        values = listOf(
                            "main", "account", "data", "ai", "ai_routing", "general", "about",
                            "trash", "backup", "language", "theme", "haptic", "background",
                            "widget", "profile", "log", "privacy", "security",
                            "local_model", "voice_library", "api_model"
                        )
                    )
                )
            ) { CommandIntent("settings_page", it.str("target"), "", "打开设置项") }
        )
        add(
            cmd(
                id = "set_theme", title = "切换外观",
                desc = "切换 App 主题：跟随系统（system）、浅色（light）、深色（dark）。",
                kind = "theme", group = "设置",
                params = listOf(
                    CapParam(
                        "mode", desc = "外观模式", required = true,
                        values = listOf("system", "light", "dark")
                    )
                ),
                reversible = true
            ) { CommandIntent("theme", it.str("mode"), "", "切换外观模式") }
        )
        add(
            cmd(
                id = "set_haptic", title = "切换触感反馈",
                desc = "触感反馈档位：跟随系统（follow）、总是开启（on）、关闭（off）。",
                kind = "haptic", group = "设置",
                params = listOf(
                    CapParam(
                        "mode", desc = "档位", required = true,
                        values = listOf("follow", "on", "off")
                    )
                ),
                reversible = true
            ) { CommandIntent("haptic", it.str("mode"), "", "切换触感反馈") }
        )
        add(
            cmd(
                id = "set_app_language", title = "切换界面语言",
                desc = "把 App 界面语言切成中文（zh）或英文（en）；AI 回复语言会跟着一起切。",
                kind = "app_language", group = "设置",
                params = listOf(
                    CapParam("lang", desc = "语言", required = true, values = listOf("zh", "en"))
                ),
                reversible = true
            ) { CommandIntent("app_language", it.str("lang"), "", "切换界面语言") }
        )
        add(
            cmd(
                id = "create_plan", title = "新建行程",
                desc = "打开新建出行计划弹窗。", kind = "create_plan", group = "调度"
            ) { CommandIntent("create_plan", "", "", "新建出行计划") }
        )
        add(
            cmd(
                id = "open_guide", title = "打开攻略",
                desc = "按城市或标题关键词打开最匹配的一篇攻略。",
                kind = "open_guide", group = "调度",
                params = listOf(CapParam("keyword", desc = "城市或标题关键词", required = true, maxLen = 40))
            ) { CommandIntent("open_guide", it.str("keyword"), "", "打开相关攻略") }
        )
        add(
            cmd(
                id = "search_guide", title = "搜攻略",
                desc = "检索并打开相关攻略，返回命中数量。",
                kind = "search_guide", group = "调度",
                params = listOf(CapParam("keyword", desc = "搜索关键词", required = true, maxLen = 40))
            ) { CommandIntent("search_guide", it.str("keyword"), "", "搜索攻略") }
        )

        // ------------------------------------------------------------ 写操作
        add(
            cmd(
                id = "toggle_like", title = "点赞攻略",
                desc = "给最匹配的一篇攻略点赞（再执行一次即取消）。",
                kind = "like_guide", group = "互动",
                params = listOf(CapParam("keyword", desc = "攻略标题或城市关键词", required = true, maxLen = 40)),
                reversible = true
            ) { CommandIntent("like_guide", it.str("keyword"), "", "点赞攻略") }
        )
        add(
            cmd(
                id = "toggle_fav", title = "收藏攻略",
                desc = "收藏最匹配的一篇攻略（再执行一次即取消）。",
                kind = "fav_guide", group = "互动",
                params = listOf(CapParam("keyword", desc = "攻略标题或城市关键词", required = true, maxLen = 40)),
                reversible = true
            ) { CommandIntent("fav_guide", it.str("keyword"), "", "收藏攻略") }
        )
        add(
            cmd(
                id = "set_weather_city", title = "切换天气城市",
                desc = "把天气页切换到指定城市（需要联网查城市坐标）。",
                kind = "weather_city", group = "天气", network = CapNetwork.REQUIRES_NETWORK,
                params = listOf(CapParam("city", desc = "城市名", required = true, maxLen = 30))
            ) { CommandIntent("weather_city", it.str("city"), "", "切换天气城市") }
        )
        add(
            cmd(
                id = "query_weather_city", title = "查异地天气",
                desc = "打开指定城市的天气页（需要联网）。",
                kind = "weather_query", group = "天气", network = CapNetwork.REQUIRES_NETWORK,
                params = listOf(CapParam("city", desc = "城市名", required = true, maxLen = 30))
            ) { CommandIntent("weather_query", it.str("city"), "", "查询异地天气") }
        )
        add(
            cmd(
                id = "setting_switch", title = "改设置开关",
                desc = "开启或关闭指定偏好开关。",
                kind = "setting", group = "设置",
                params = listOf(
                    CapParam(
                        "target", desc = "开关名", required = true,
                        values = listOf("defaultPublic", "notifEnabled", "shareEnabled", "ai_ball", "voice_input", "voice_tts")
                    ),
                    CapParam("enabled", type = CapType.BOOL, desc = "true 开启 / false 关闭", required = true)
                )
            ) {
                CommandIntent(
                    "setting", it.str("target"), it.bool("enabled").toString(),
                    "修改设置"
                )
            }
        )
        add(
            cmd(
                id = "set_chat_mode", title = "切换对话模型",
                desc = "把当前对话模型切成本地模型或云端模型（就是对话框底部那一项）。",
                kind = "set_chat_mode", group = "设置",
                params = listOf(
                    CapParam(
                        "mode", desc = "目标模型", required = true,
                        // hybrid 是旧协议遗留值，App 端按 api 处理，避免老模型输出被判参数非法
                        values = listOf("local", "api", "hybrid")
                    )
                )
            ) { CommandIntent("set_chat_mode", it.str("mode"), "", "切换对话模型") }
        )
        add(
            cmd(
                id = "set_language", title = "切语言",
                desc = "切换 AI 回复语言为中文或英文。",
                kind = "language", group = "设置",
                params = listOf(
                    CapParam("lang", desc = "语言", required = true, values = listOf("zh", "en"))
                )
            ) { CommandIntent("language", it.str("lang"), "", "切换语言") }
        )
        add(
            Capability(
                id = "edit_footprint_note",
                title = "改足迹备注",
                desc = "修改某条足迹的备注文案。关键词必须唯一命中，否则拒绝执行以免改错。",
                group = "编辑", risk = CapRisk.WRITE,
                params = listOf(
                    CapParam("keyword", desc = "足迹地点名或城市", required = true, maxLen = 40),
                    CapParam("note", desc = "新备注", required = true, maxLen = 200)
                ),
                backedBy = setOf("applyCommand", "footprints"),
                execute = {
                    val key = str("keyword")
                    val note = str("note")
                    // 旧实现用 contains 模糊匹配直接改第一条，模型给个短词就可能改错数据。
                    // 这里改成「必须唯一命中」，命中多条时把候选列出来让模型/用户补信息。
                    val hits = state.footprints.filter {
                        it.name.equals(key, true) || it.name.contains(key, true) || it.city.contains(key, true)
                    }
                    when {
                        hits.isEmpty() -> CapResult(false, "没找到与「$key」相关的足迹", "请让用户给出更准确的地点名")
                        hits.size > 1 -> CapResult(
                            false, "「$key」匹配到 ${hits.size} 条足迹，不能确定改哪条",
                            "候选：" + hits.take(5).joinToString("、") { "${it.city}·${it.name}" } + "；请反问用户"
                        )
                        else -> {
                            state.applyCommand(CommandIntent("edit_footprint", key, note, "修改足迹备注"))
                            CapResult(true, "已更新「${hits.first().name}」的备注", changedIds = listOf(hits.first().id.toString()))
                        }
                    }
                }
            )
        )

        // ------------------------------------------------------------ 剩余旧动作（P1 迁移完成）
        add(
            cmd(
                id = "logout", title = "退出登录",
                desc = "退出当前账号、清除云端关联数据；设备上的足迹与行程会保留。",
                kind = "logout", group = "账号", risk = CapRisk.WRITE
            ) { CommandIntent("logout", "", "", "退出登录") }
        )
        add(
            cmd(
                id = "set_local_model", title = "切换本地模型",
                desc = "切换到已下载的端侧模型；模型没下载时会明确拒绝。",
                kind = "set_local_model", group = "设置", risk = CapRisk.WRITE,
                params = listOf(CapParam("model", desc = "模型名称或 id 片段", required = true, maxLen = 60))
            ) { CommandIntent("set_local_model", it.str("model"), "", "切换本地模型") }
        )

        // ------------------------------------------------------------ 破坏性：二次确认 + 可撤销
        add(
            Capability(
                id = "delete_trip", title = "删除行程",
                desc = "按名称删除一条行程。必须唯一命中，多条候选会拒绝并要求用户补充；可撤销。",
                group = "破坏性", risk = CapRisk.DESTRUCTIVE,
                params = listOf(CapParam("keyword", desc = "行程名称或目的地", required = true, maxLen = 40)),
                backedBy = setOf("deleteTripPlan", "restoreTripPlan"),
                execute = {
                    val key = str("keyword")
                    // 旧 applyCommand 用 contains 取第一条，模型给个短词就可能删错行程。
                    // 删除是不可逆操作，这里必须唯一命中。
                    val hits = state.tripPlans.filter {
                        it.name.equals(key, true) || it.name.contains(key, true) ||
                            it.destination.contains(key, true)
                    }
                    when {
                        hits.isEmpty() -> CapResult(false, "没找到与「$key」相关的行程", "请让用户给出更准确的行程名")
                        hits.size > 1 -> CapResult(
                            false, "「$key」匹配到 ${hits.size} 条行程，不能确定删哪条",
                            "候选：" + hits.take(5).joinToString("、") { it.name } + "；请反问用户"
                        )
                        else -> {
                            val plan = hits.first()
                            state.deleteTripPlan(plan.id)
                            CapResult(
                                true, "已删除行程《${plan.name}》",
                                data = "deleted trip ${plan.id}",
                                changedIds = listOf(plan.id.toString()),
                                // 快照恢复：内存 + 落盘都还原，撤销后重启也还在
                                undo = {
                                    if (state.tripPlans.any { it.id == plan.id }) false
                                    else {
                                        state.restoreTripPlan(plan)
                                        true
                                    }
                                }
                            )
                        }
                    }
                }
            )
        )
        add(
            cmd(
                id = "exit_app", title = "退出应用",
                desc = "关闭 App；真正 finish Activity 由 UI 层完成。",
                kind = "exit_app", group = "破坏性", risk = CapRisk.DESTRUCTIVE
            ) { CommandIntent("exit_app", "", "", "退出应用") }
        )
    }

    /** 转发到既有 `applyCommand`：P0 不改变任何旧行为，只把入口统一到网关。
     *  reversible=true 用于点赞/收藏这类可再点一次还原的低风险 toggle，网关免确认直接执行。 */
    private fun cmd(
        id: String,
        title: String,
        desc: String,
        kind: String,
        group: String,
        risk: CapRisk = CapRisk.WRITE,
        network: CapNetwork = CapNetwork.LOCAL,
        params: List<CapParam> = emptyList(),
        reversible: Boolean = false,
        map: (CapContext) -> CommandIntent
    ): Capability = Capability(
        id = id, title = title, desc = desc, group = group, risk = risk, network = network,
        params = params, backedBy = setOf("applyCommand"), reversible = reversible,
        execute = {
            val intent = map(this)
            state.applyCommand(intent)
            CapResult(
                ok = true,
                message = intent.label.ifBlank { title },
                data = "已执行 ${intent.kind}(target=${intent.target}, value=${intent.value})"
            )
        }
    )

    fun all(): List<Capability> = items

    /** 可导出给模型的能力；gated 的破坏性能力绝不出现在工具表里 */
    fun visible(): List<Capability> = items.filter { !it.gated }

    fun find(id: String): Capability? = items.firstOrNull { it.id == id.trim() }

    /** 预检链路只允许静默调用只读、已开放的能力 */
    fun isReadOnlyTool(id: String): Boolean =
        find(id)?.let { !it.gated && it.risk == CapRisk.READ } == true

    /**
     * 可被 ReAct 自动调用的能力：只读，或明确登记为可逆的低风险写操作
     * （例如播放/暂停、点赞/收藏）。删除、退出、隐私开关等仍绝不自动执行。
     */
    fun isAutoTool(id: String): Boolean =
        find(id)?.let { !it.gated && (it.risk == CapRisk.READ || it.reversible) } == true

    /**
     * OpenAI Function Calling 工具表。
     *
     * 预检是「自动执行、用户看不到确认卡」的路径，所以调用方必须传 [readOnlyOnly] = true：
     * 只把只读与可逆的低风险能力交给模型直接 Function Calling，其他写入/破坏性动作仍走
     * <intent> 确认卡。这条约束是安全底线，不能因为「接得全」就把写权限交给一次静默预检。
     */
    fun toolsJson(allowNetwork: Boolean = true, readOnlyOnly: Boolean = false): JSONArray = JSONArray().apply {
        visible()
            .filter { allowNetwork || it.network != CapNetwork.REQUIRES_NETWORK }
            .filter { !readOnlyOnly || it.risk == CapRisk.READ || it.reversible }
            .forEach { put(it.schema()) }
    }

    /**
     * 注入 system 的能力清单。只告知「App 能做什么」，不另造一套指令协议，
     * 具体下发仍走现有 <intent>，避免和已调优的意图链路打架。
     */
    fun describe(): String {
        val vis = visible()
        val auto = vis.filter { it.risk == CapRisk.READ || it.reversible }
        val read = auto.filter { it.risk == CapRisk.READ }.joinToString("、") { it.id }
        val reversible = auto.filter { it.risk == CapRisk.WRITE }.joinToString("、") { it.id }
        val write = vis.filter { it.risk == CapRisk.WRITE && !it.reversible }.joinToString("、") { it.id }
        val danger = vis.filter { it.risk == CapRisk.DESTRUCTIVE }.joinToString("、") { it.id }
        return "【App 可调度能力清单（以能力注册表为准）】\n" +
            "只读：$read\n" +
            "可逆、可自动执行（播放/暂停这类，无需确认）：$reversible\n" +
            "需用户确认后执行：$write\n" +
            "高风险、需二次确认且用户可撤销：$danger\n" +
            "说明：这份清单只告诉你 App 真正能做什么，下发指令仍按上面的 <intent> 协议；" +
            "「播放歌曲」「查火车票/机票」都是 App 的真实能力，不要说做不到，也不要把网页入口当成结果；" +
            "高风险操作必须写明具体对象，App 会拒绝模糊匹配；" +
            "清单里没有的操作要明确说做不到，不要承诺，也不要假装已经执行。"
    }

    /**
     * 覆盖率审计：把 AppState 里疑似动作方法与已登记能力做差集。
     *
     * 反射出来的候选会混入 Compose 属性 setter（如 setFootprints），所以这是**提示性**清单，
     * 供人工确认下一批要接什么，不是精确的功能覆盖率。真正的精确指标是
     * 「已登记能力数 / 已确认动作数」，由 P1 逐项核销。
     */
    fun audit(): String {
        val vis = visible()
        val byRisk = CapRisk.entries.associateWith { r -> vis.count { it.risk == r } }
        val prefixes = listOf(
            "open", "close", "toggle", "set", "add", "delete", "update", "apply",
            "publish", "mark", "restore", "save", "clear", "batch", "import", "export", "archive"
        )
        val candidates = runCatching {
            AppState::class.java.declaredMethods
                .map { it.name }
                .filter { !it.contains("$") && !it.startsWith("get") }
                .filter { n -> prefixes.any { n.startsWith(it) } }
                .distinct().sorted()
        }.getOrDefault(emptyList())
        val covered = items.flatMap { it.backedBy }.toSet()
        val missing = candidates.filter { it !in covered }
        return buildString {
            appendLine("能力注册表：共 ${items.size} 项，开放 ${vis.size} 项，暂缓 ${items.count { it.gated }} 项")
            appendLine("按风险：只读 ${byRisk[CapRisk.READ]} / 写入 ${byRisk[CapRisk.WRITE]} / 破坏性 ${byRisk[CapRisk.DESTRUCTIVE]}")
            appendLine("AppState 疑似动作方法 ${candidates.size} 个，已被能力表引用 ${candidates.count { it in covered }} 个")
            appendLine("未接入候选（含属性 setter，需人工确认）：${missing.take(25).joinToString("、").ifBlank { "无" }}")
        }.trim()
    }
}
