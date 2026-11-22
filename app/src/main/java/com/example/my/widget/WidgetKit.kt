package com.example.my.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import com.example.my.FootprintWidgetProvider
import com.example.my.MainActivity
import com.example.my.MusicWidgetProvider
import com.example.my.R
import com.example.my.data.Persistence
import com.example.my.data.WeatherApi
import com.example.my.data.local.OfflineCache
import com.example.my.model.Footprint
import com.example.my.model.Guide
import com.example.my.model.TripPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 桌面小组件公共层。
 *
 * 设计稿是一组浅色圆角卡片（暖白 + 赤陶橙），数据必须来自 App 本地真源：
 *  - 足迹 / 攻略 → Room（[OfflineCache]）
 *  - 出行计划 → DataStore（[Persistence.loadTripPlans]）
 *  - 主题 / 语言 → SharedPreferences `app_settings`
 *  - 天气实况 → 小组件自己的 SP 缓存（30 分钟节流，不每次刷新都发网络请求）
 *
 * RemoteViews 不走 `values-en`，也不跟随 Compose 主题，所以语言与配色都在这里
 * 显式解析并写进视图；切语言 / 切主题后由设置页调用 [refreshAll]。
 */
object WidgetKit {

    const val EXTRA_TARGET = "widget_target"
    const val EXTRA_GUIDE_ID = "widget_guide_id"

    /** 卡片上「点图标重新加载」用的刷新广播，避开系统 30 分钟的最短更新周期 */
    const val ACTION_REFRESH = "com.example.my.widget.ACTION_REFRESH"

    const val TARGET_MAP = "map"
    const val TARGET_WEATHER = "weather"
    const val TARGET_PLANS = "plans"
    const val TARGET_CAPTURE = "capture"
    const val TARGET_GUIDE = "guide"
    const val TARGET_MUSIC = "music"
    const val TARGET_STATS = "stats"
    /** 课表卡点开直接进课表页，而不是绕到「我的计划」 */
    const val TARGET_COURSE = "course"

    private const val SETTINGS_PREFS = "app_settings"
    private const val WIDGET_PREFS = "widget_data"
    private const val KEY_LANG = "app_language"
    private const val KEY_THEME = "theme_mode"

    private const val W_CITY = "w_city"
    private const val W_TEMP = "w_temp"
    private const val W_DESC = "w_desc"
    private const val W_EMOJI = "w_emoji"
    private const val W_TIME = "w_time"
    /** 用户自定义的语录文字（空串 = 用内置每日一句） */
    private const val W_QUOTE = "w_quote"

    /** 默认天气城市（与 AppState 的初始值一致） */
    private const val DEFAULT_CITY = "重庆"
    private const val DEFAULT_LAT = 29.5630
    private const val DEFAULT_LON = 106.5516
    private const val WEATHER_TTL = 30L * 60L * 1000L
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    // ---------- 主题 / 语言 ----------

    private fun settings(context: Context) =
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)

    private fun widgetPrefs(context: Context) =
        context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)

    /** 0=跟随系统，1=浅色，2=深色 */
    fun isDark(context: Context): Boolean {
        return when (settings(context).getInt(KEY_THEME, 0)) {
            1 -> false
            2 -> true
            else -> {
                val mask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                mask == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    fun isEn(context: Context): Boolean {
        val saved = settings(context).getString(KEY_LANG, null)
        return saved?.let { it == "en" } ?: (Locale.getDefault().language == "en")
    }

    /** 小组件专用双语取词：读 SP 而不是 Lang.code（小组件可能在独立进程 / 冷启动） */
    fun t(context: Context, zh: String, en: String): String = if (isEn(context)) en else zh

    /** 设计稿色板；深色是同一套语义色的暗化版本 */
    data class C(
        val card: Int,
        val text: Int,
        val sub: Int,
        val muted: Int,
        val accent: Int,
        val accentSoft: Int,
        val track: Int,
        val tile: Int,
        val land: Int,
        val landLine: Int,
        val water: Int,
        val grid: Int,
        val onAccent: Int
    )

    fun colors(context: Context): C = if (isDark(context)) C(
        card = 0xFF26211E.toInt(),
        text = 0xFFF3EEE9.toInt(),
        sub = 0xFFC5BBB0.toInt(),
        muted = 0xFF8A8178.toInt(),
        accent = 0xFFE07A5F.toInt(),
        accentSoft = 0xFFF4A261.toInt(),
        track = 0xFF3C3530.toInt(),
        tile = 0xFF312B27.toInt(),
        land = 0xFF2E352E.toInt(),
        landLine = 0xFF465448.toInt(),
        water = 0xFF1F2B33.toInt(),
        grid = 0xFF4A413B.toInt(),
        onAccent = 0xFFFFFFFF.toInt()
    ) else C(
        card = 0xFFFDFBF9.toInt(),
        text = 0xFF1F2328.toInt(),
        sub = 0xFF5F666E.toInt(),
        muted = 0xFF9AA0A6.toInt(),
        accent = 0xFFC1624A.toInt(),
        accentSoft = 0xFFE07A5F.toInt(),
        track = 0xFFE9E4E0.toInt(),
        tile = 0xFFF0ECE9.toInt(),
        land = 0xFFF1F5EE.toInt(),
        landLine = 0xFFE1E9DF.toInt(),
        water = 0xFFE6F1FA.toInt(),
        grid = 0xFFE5DED8.toInt(),
        onAccent = 0xFFFFFFFF.toInt()
    )

    /**
     * 所有小组件布局的根 id 统一为 widget_root，这里一处切换浅/深卡片背景。
     * [glass] = true 时换成课表专用的半透明玻璃底（能透出壁纸），其余卡片仍是不透明纸卡。
     */
    fun decorate(context: Context, v: RemoteViews, glass: Boolean = false) {
        val resId = when {
            glass && isDark(context) -> R.drawable.widget_course_glass_dark
            glass -> R.drawable.widget_course_glass
            isDark(context) -> R.drawable.widget_card_dark
            else -> R.drawable.widget_card
        }
        v.setInt(R.id.widget_root, "setBackgroundResource", resId)
    }

    // ---------- 数据聚合 ----------

    data class WeatherInfo(
        val city: String,
        val temp: Int?,
        val desc: String,
        val emoji: String
    )

    data class Snapshot(
        val footprints: List<Footprint>,
        val guides: List<Guide>,
        val plans: List<TripPlan>,
        val weather: WeatherInfo,
        val todayCount: Int,
        val todayCities: Int,
        val monthCount: Int,
        val monthBars: List<Pair<String, Int>>,
        val cityCount: Int,
        val points: List<Pair<Double, Double>>,
        val nextPlan: TripPlan?,
        val daysLeft: Int,
        val topGuide: Guide?,
        val goalPercent: Int,
        val courses: List<com.example.my.model.Course> = emptyList(),
        /** 课表作息设置：小组件里的节次时间跟 App 内设置保持一致 */
        val courseTable: com.example.my.model.CourseTableSettings = com.example.my.model.CourseTableSettings()
    )

    fun cachedWeather(context: Context): WeatherInfo {
        val p = widgetPrefs(context)
        return WeatherInfo(
            city = p.getString(W_CITY, DEFAULT_CITY) ?: DEFAULT_CITY,
            temp = if (p.contains(W_TEMP)) p.getInt(W_TEMP, 0) else null,
            desc = p.getString(W_DESC, "").orEmpty(),
            emoji = p.getString(W_EMOJI, "晴").orEmpty()
        )
    }

    /** 天气只在超过 30 分钟时 quietly 刷新一次；失败保留旧缓存，小组件永远不空白 */
    suspend fun refreshWeatherIfStale(context: Context) {
        val p = widgetPrefs(context)
        if (System.currentTimeMillis() - p.getLong(W_TIME, 0L) < WEATHER_TTL) return
        val city = p.getString(W_CITY, DEFAULT_CITY) ?: DEFAULT_CITY
        // 广播接收器只有约 10 秒窗口，网络再慢也不能拖死刷新（失败就继续用旧缓存）
        runCatching {
            val s = withTimeout(8_000L) {
                WeatherApi.fetch(city, DEFAULT_LAT, DEFAULT_LON)
            }
            p.edit()
                .putString(W_CITY, s.city.ifBlank { city })
                .putInt(W_TEMP, s.temp)
                .putString(W_DESC, s.desc)
                .putString(W_EMOJI, s.emoji)
                .putLong(W_TIME, System.currentTimeMillis())
                .apply()
        }
    }

    /**
     * 不联网、不读 Room / DataStore 的骨架数据。
     *
     * 小组件刚被拖到桌面时进程往往是冷启动，`onUpdate` 只有约 10 秒广播窗口；
     * 若把首次渲染押在 Room 打开 + DataStore 首次读 + 天气网络请求上，系统一超时
     * 就会杀掉进程，桌面会永远停在 ROM 的「加载转圈」白卡。先同步刷这一版即可根治。
     */
    fun baseline(context: Context): Snapshot = Snapshot(
        footprints = emptyList(),
        guides = emptyList(),
        plans = emptyList(),
        weather = cachedWeather(context),
        todayCount = 0,
        todayCities = 0,
        monthCount = 0,
        monthBars = monthLabels().map { it.second to 0 },
        cityCount = 0,
        points = emptyList(),
        nextPlan = null,
        daysLeft = -1,
        topGuide = null,
        goalPercent = 0
    )

    /** 天气网络请求只放在独立后台 job，绝不阻塞小组件渲染；成功后单独刷天气卡 */
    fun refreshWeatherAsync(context: Context) {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val before = cachedWeather(context)
            runCatching { withTimeout(5_000L) { refreshWeatherIfStale(context) } }
            if (before != cachedWeather(context)) {
                runCatching { refresh(context, WeatherWidgetProvider::class.java) }
            }
        }
    }

    private fun monthLabels(): List<Pair<String, String>> = (5 downTo 0).map { offset ->
        val c = Calendar.getInstance()
        c.add(Calendar.MONTH, -offset)
        SimpleDateFormat("yyyy-MM", Locale.CHINA).format(c.time) to
            c.get(Calendar.MONTH).plus(1).toString()
    }

    suspend fun load(context: Context): Snapshot = withContext(Dispatchers.IO) {
        val footprints = runCatching { OfflineCache.loadFootprints(context) }.getOrNull().orEmpty()
        val guides = runCatching { OfflineCache.loadGuides(context) }.getOrNull().orEmpty()
        val plans = runCatching { Persistence.loadTripPlans(context) }.getOrNull().orEmpty()

        val today = startOfToday()
        val todayList = footprints.filter { footprintMillis(it) >= today }
        val monthFmt = SimpleDateFormat("yyyy-MM", Locale.CHINA)
        val thisMonth = monthFmt.format(Date())
        val monthCount = footprints.count { monthFmt.format(Date(footprintMillis(it))) == thisMonth }

        val labels = (5 downTo 0).map { offset ->
            val c = Calendar.getInstance()
            c.add(Calendar.MONTH, -offset)
            monthFmt.format(c.time)
        }
        val byMonth = footprints.groupBy { monthFmt.format(Date(footprintMillis(it))) }
        val bars = labels.map { label ->
            (label.substringAfter("-").trimStart('0').ifEmpty { "0" }) to (byMonth[label]?.size ?: 0)
        }

        val points = footprints.mapNotNull { fp ->
            val lat = fp.lat
            val lon = fp.lon
            if (lat != null && lon != null && lat in 3.0..54.0 && lon in 73.0..136.0) {
                lat to lon
            } else {
                // 旧数据只有示意投影坐标（0-774 / 0-569），粗略反算用于桌面缩略图
                val x = fp.x.toDouble().coerceIn(0.0, 774.0)
                val y = fp.y.toDouble().coerceIn(0.0, 569.0)
                val lo = 73.0 + x / 774.0 * 62.0
                val la = 54.0 - y / 569.0 * 36.0
                la to lo
            }
        }.take(28)

        val upcoming = plans.filter { !it.archived }
            .mapNotNull { p -> daysUntil(p.startDate)?.let { if (it >= 0) p to it else null } }
            .minByOrNull { it.second }

        val topGuide = guides.firstOrNull { it.featured } ?: guides.firstOrNull()
        val goalPercent = (todayList.size * 100 / 3).coerceIn(0, 100)

        Snapshot(
            footprints = footprints,
            guides = guides,
            plans = plans,
            weather = cachedWeather(context),
            todayCount = todayList.size,
            todayCities = todayList.map { it.city }.filter { it.isNotBlank() }.distinct().size,
            monthCount = monthCount,
            monthBars = bars,
            cityCount = footprints.map { it.city }.filter { it.isNotBlank() }.distinct().size,
            points = points,
            nextPlan = upcoming?.first,
            daysLeft = upcoming?.second ?: -1,
            topGuide = topGuide,
            goalPercent = goalPercent,
            courses = Persistence.loadCourses(context),
            courseTable = Persistence.loadCourseTable(context)
        )
    }

    fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun footprintMillis(fp: Footprint): Long = fp.dateMillis ?: parseDate(fp.date) ?: 0L

    private fun parseDate(raw: String): Long? {
        if (raw.isBlank()) return null
        val patterns = listOf("yyyy-MM-dd", "yyyy/MM/dd", "yyyy年M月d日", "yyyy年M月", "yyyy.MM.dd")
        for (p in patterns) {
            val t = runCatching {
                SimpleDateFormat(p, Locale.CHINA).parse(raw)?.time
            }.getOrNull()
            if (t != null) return t
        }
        return null
    }

    private fun daysUntil(startDate: String): Int? {
        val d = parseDate(startDate) ?: return null
        return ((d - startOfToday()) / DAY_MS).toInt()
    }

    /** 用户自定义的语录；空串表示用内置每日一句 */
    fun customQuote(context: Context): String =
        widgetPrefs(context).getString(W_QUOTE, "").orEmpty().trim()

    /** 保存自定义语录；传空串即恢复内置每日一句 */
    fun setCustomQuote(context: Context, text: String) {
        widgetPrefs(context).edit().putString(W_QUOTE, text.trim()).apply()
    }

    /** 每天换一句：旅行语录小组件（用户自定义过就用用户自己写的） */
    fun quoteOfDay(context: Context): Pair<String, String> {
        val custom = customQuote(context)
        if (custom.isNotEmpty()) {
            return custom to if (isEn(context)) "MY TRACE" else "MY足迹"
        }
        val quotes = listOf(
            "身体和灵魂，\n总有一个在路上" to "Body and soul,\none is always on the road",
            "步履不停，\n风景自来" to "Keep walking,\nthe view will find you",
            "把世界，\n走成自己的地图" to "Walk the world\ninto your own map",
            "去更远的地方，\n见更亮的自己" to "Go further,\nmeet a brighter you"
        )
        val day = ((System.currentTimeMillis() / DAY_MS) % quotes.size).toInt()
        val q = quotes[day]
        return if (isEn(context)) q.second to "MY TRACE" else q.first to "MY足迹"
    }

    // ---------- 点击 / 刷新 ----------

    fun openPending(
        context: Context, target: String, req: Int, guideId: Int = 0
    ): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET, target)
            if (guideId > 0) putExtra(EXTRA_GUIDE_ID, guideId)
        }
        return PendingIntent.getActivity(
            context, req, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun bindOpen(
        context: Context, v: RemoteViews, viewId: Int,
        target: String, req: Int, guideId: Int = 0
    ) {
        v.setOnClickPendingIntent(viewId, openPending(context, target, req, guideId))
    }

    /** 全部小组件类型，切语言 / 切主题 / 数据落盘后一处刷新 */
    fun providerClasses(): List<Class<*>> = listOf(
        FootprintWidgetProvider::class.java,
        MusicWidgetProvider::class.java,
        TodayWidgetProvider::class.java,
        WeatherWidgetProvider::class.java,
        QuoteWidgetProvider::class.java,
        DepartureWidgetProvider::class.java,
        CityMapWidgetProvider::class.java,
        MonthlyWidgetProvider::class.java,
        GuideWidgetProvider::class.java,
        CaptureWidgetProvider::class.java,
        CourseWidgetProvider::class.java
    )

    /** 卡片上的刷新按钮：广播给自己，接收端会重走一遍 baseline + 真实数据 */
    fun refreshPending(context: Context, clazz: Class<*>, req: Int): PendingIntent {
        val i = Intent(context, clazz).apply {
            action = ACTION_REFRESH
        }
        return PendingIntent.getBroadcast(
            context, req, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun refresh(context: Context, clazz: Class<*>) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = runCatching {
            mgr.getAppWidgetIds(ComponentName(context, clazz))
        }.getOrNull() ?: return
        if (ids.isEmpty()) return
        // 注意：不能再发 ACTION_APPWIDGET_UPDATE —— 它是系统保护广播，
        // 第三方 App 发出去会被系统以 Permission Denial 拒掉（此前 snoop 不到的静默失败）。
        // 改为发我们自己的刷新 action，由 BaseWidget.onReceive 转回 onUpdate。
        runCatching {
            context.sendBroadcast(Intent(context, clazz).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            })
        }
    }

    fun refreshAll(context: Context) {
        providerClasses().forEach { refresh(context, it) }
    }
}
