package com.example.my.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.example.my.R
import com.example.my.model.Course
import com.example.my.util.SplashGate
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * 设计稿里的 8 张新卡片，共用一套渲染骨架。
 *
 * 小组件进程里没有 AppState，数据一律走 [WidgetKit.load]（Room + DataStore + SP）；
 * `goAsync()` 让广播接收器等协程读完再结束，避免系统提前回收导致桌面一直显示占位。
 */
abstract class BaseWidget : AppWidgetProvider() {

    protected abstract val layout: Int

    /** 天气卡需要顺便刷新天气缓存（30 分钟节流） */
    protected open val withWeather: Boolean = false

    /** 课表卡用半透明玻璃底（透出壁纸），其余卡片仍是不透明纸卡 */
    protected open val glass: Boolean = false

    protected abstract fun render(
        context: Context, v: RemoteViews, d: WidgetKit.Snapshot, c: WidgetKit.C
    )

    /**
     * 按桌面实际尺寸自适应的渲染入口。默认走 [render]；课表卡覆写它，
     * 按可用高度决定放几节课、按宽度决定要不要显示教室，内容永远不会被卡片裁掉。
     */
    protected open fun renderSized(
        context: Context, v: RemoteViews, d: WidgetKit.Snapshot, c: WidgetKit.C,
        wDp: Int, hDp: Int
    ) = render(context, v, d, c)

    /** 桌面实际给的宽高（dp）；拿不到 options 时按 2x2 的常见尺寸兜底 */
    private fun widgetSize(mgr: AppWidgetManager, id: Int, options: Bundle? = null): Pair<Int, Int> {
        val o = options ?: runCatching { mgr.getAppWidgetOptions(id) }.getOrNull()
        fun pick(keys: Array<String>, fallback: Int): Int {
            for (k in keys) {
                val n = o?.getInt(k, -1) ?: -1
                if (n > 0) return n
            }
            return fallback
        }
        val w = pick(
            arrayOf(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
            160
        )
        val h = pick(
            arrayOf(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
            160
        )
        return w to h
    }

    final override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        // ① 同步刷一版完整骨架：不读 Room / DataStore、不联网，几十毫秒内完成。
        //    这样即使后面协程被系统杀掉，桌面也有完整卡片，不会停在加载转圈的白卡。
        runCatching { drawSafely(context, mgr, ids, WidgetKit.baseline(context)) }

        // ② 天气网络刷新完全脱离渲染关键路径，成功后自己再刷一次天气卡
        if (withWeather) WidgetKit.refreshWeatherAsync(context)

        // ③ 再异步补真实数据；6 秒拿不到就保留骨架
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val d = try {
                    withTimeout(6_000L) { WidgetKit.load(context) }
                } catch (_: Throwable) {
                    WidgetKit.baseline(context)
                }
                drawSafely(context, mgr, ids, d)
            } catch (_: Throwable) {
                // 骨架已经刷过，绝不把桌面回退成空白
            } finally {
                pending.finish()
            }
        }
    }

    /** 卡片上的刷新图标走这里：系统广播被 ROM 拦掉时仍有一条用户可点的自救通道 */
    final override fun onReceive(context: Context, intent: Intent) {
        // 只有系统发来的 APPWIDGET_UPDATE / OPTIONS_CHANGED 才是后台单独拉起进程；
        // App 自己发 ACTION_REFRESH 刷新卡片时进程里已有界面，不能误伤冷启动的启动页。
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE ||
            intent.action == AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED
        ) {
            SplashGate.onBackgroundUse()
        }
        if (intent.action == WidgetKit.ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                ?.takeIf { it.isNotEmpty() }
                ?: runCatching {
                    mgr.getAppWidgetIds(ComponentName(context, this::class.java))
                }.getOrNull()
                ?: return
            onUpdate(context, mgr, ids)
            return
        }
        super.onReceive(context, intent)
    }

    /** 桌面改尺寸 / ROM 只发 optionsChanged 不发 update 时，也把卡片画一遍 */
    final override fun onAppWidgetOptionsChanged(
        context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, mgr, id, newOptions)
        // ① 同步刷骨架，用新尺寸立刻重排，避免拉伸后还留着旧布局
        val (w, h) = widgetSize(mgr, id, newOptions)
        runCatching {
            val v = RemoteViews(context.packageName, layout)
            WidgetKit.decorate(context, v, glass)
            renderSized(context, v, WidgetKit.baseline(context), WidgetKit.colors(context), w, h)
            mgr.updateAppWidget(id, v)
        }
        // ② 原来这里只刷 baseline，尺寸变化后课表会错误地显示「今天没课」；
        //    补一次异步真实数据，口径与 onUpdate 一致
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val d = try {
                    withTimeout(6_000L) { WidgetKit.load(context) }
                } catch (_: Throwable) {
                    WidgetKit.baseline(context)
                }
                drawSafely(context, mgr, intArrayOf(id), d)
            } catch (_: Throwable) {
                // 骨架已经刷过
            } finally {
                pending.finish()
            }
        }
    }

    /** 每个 widget 独立容错；带 Bitmap 的 RemoteViews 太大导致 Binder 失败时降级刷纯文本版 */
    private fun drawSafely(
        context: Context, mgr: AppWidgetManager, ids: IntArray, d: WidgetKit.Snapshot
    ) {
        val c = WidgetKit.colors(context)
        for (id in ids) {
            try {
                val v = RemoteViews(context.packageName, layout)
                WidgetKit.decorate(context, v, glass)
                val (w, h) = widgetSize(mgr, id)
                renderSized(context, v, d, c, w, h)
                try {
                    mgr.updateAppWidget(id, v)
                } catch (_: Throwable) {
                    // 多数是位图过大触发 TransactionTooLargeException：退到布局自带的静态文案。
                    val fallback = RemoteViews(context.packageName, layout)
                    runCatching { WidgetKit.decorate(context, fallback, glass) }
                    mgr.updateAppWidget(id, fallback)
                }
            } catch (_: Throwable) {
                runCatching {
                    mgr.updateAppWidget(id, RemoteViews(context.packageName, layout))
                }
            }
        }
    }
}

/** 1｜今日足迹：分段进度环 + 今日条数 / 城市数 */
class TodayWidgetProvider : BaseWidget() {
    override val layout = R.layout.widget_today

    override fun render(context: Context, v: RemoteViews, d: WidgetKit.Snapshot, c: WidgetKit.C) {
        v.setTextViewText(R.id.widget_today_title, WidgetKit.t(context, "今日足迹", "Today's trace"))
        v.setTextViewText(R.id.widget_today_percent, "${d.goalPercent}%")
        v.setTextViewText(
            R.id.widget_today_summary,
            WidgetKit.t(
                context,
                "今日 ${d.todayCount} 条足迹 · ${d.todayCities} 个城市",
                "Today: ${d.todayCount} footprints · ${d.todayCities} cities"
            )
        )
        v.setTextColor(R.id.widget_today_title, c.text)
        v.setTextColor(R.id.widget_today_percent, c.text)
        v.setTextColor(R.id.widget_today_summary, c.sub)
        v.setImageViewBitmap(R.id.widget_today_ring, WidgetArt.ring(d.goalPercent, c))
        v.setImageViewBitmap(
            R.id.widget_today_icon,
            WidgetArt.vector(context, R.drawable.ic_widget_refresh, 48, c.muted)
        )
        // 点右上角小图标 = 立刻重载一次（天气 / 数据没刷出来时的自救按钮）
        v.setOnClickPendingIntent(
            R.id.widget_today_icon,
            WidgetKit.refreshPending(context, TodayWidgetProvider::class.java, 111)
        )
        WidgetKit.bindOpen(context, v, R.id.widget_root, WidgetKit.TARGET_MAP, 101)
    }
}

/** 2｜天气：实况 + 行程倒计时 + 近半年足迹方格 */
class WeatherWidgetProvider : BaseWidget() {
    override val layout = R.layout.widget_weather
    override val withWeather = true

    override fun render(context: Context, v: RemoteViews, d: WidgetKit.Snapshot, c: WidgetKit.C) {
        val w = d.weather
        val en = WidgetKit.isEn(context)
        v.setTextViewText(R.id.widget_weather_title, WidgetKit.t(context, "天气", "Weather"))
        v.setTextViewText(R.id.widget_weather_temp, w.temp?.let { "$it°C" } ?: "--°C")

        val desc = if (en) weatherEn(w.desc) else w.desc
        val tomorrow = WidgetKit.t(context, "明天", "Tomorrow")
        v.setTextViewText(
            R.id.widget_weather_desc,
            "${w.city} · ${desc.ifBlank { WidgetKit.t(context, "晴", "Clear") }} · $tomorrow"
        )
        val plan = d.nextPlan
        v.setTextViewText(
            R.id.widget_weather_countdown,
            if (plan == null) WidgetKit.t(context, "暂无行程倒计时", "No upcoming trip")
            else {
                val dest = plan.destination.ifBlank { plan.name }
                WidgetKit.t(
                    context,
                    "$dest 行程倒计时 ${d.daysLeft} 天",
                    "$dest trip in ${d.daysLeft} days"
                )
            }
        )
        listOf(
            R.id.widget_weather_title, R.id.widget_weather_desc
        ).forEach { v.setTextColor(it, c.text) }
        v.setTextColor(R.id.widget_weather_temp, c.accent)
        v.setTextColor(R.id.widget_weather_countdown, c.sub)
        v.setImageViewBitmap(R.id.widget_weather_sun, WidgetArt.sun(c))
        v.setImageViewBitmap(
            R.id.widget_weather_icon,
            WidgetArt.vector(context, R.drawable.ic_widget_cloud, 48, c.muted)
        )
        // 点右上角小云朵 = 立即重载（含天气网络刷新）
        v.setOnClickPendingIntent(
            R.id.widget_weather_icon,
            WidgetKit.refreshPending(context, WeatherWidgetProvider::class.java, 112)
        )
        v.setImageViewBitmap(
            R.id.widget_weather_tiles,
            WidgetArt.tiles(d.monthBars.map { it.first }, d.monthBars.map { it.second }, c)
        )
        WidgetKit.bindOpen(context, v, R.id.widget_root, WidgetKit.TARGET_WEATHER, 102)
    }

    /** 天气描述是接口返回的中文，英文模式做一层轻量映射 */
    private fun weatherEn(desc: String): String = when {
        desc.contains("晴") -> "Sunny"
        desc.contains("大雨") || desc.contains("暴雨") -> "Heavy rain"
        desc.contains("雨") -> "Rainy"
        desc.contains("雪") -> "Snowy"
        desc.contains("雾") || desc.contains("霾") -> "Foggy"
        desc.contains("云") || desc.contains("阴") -> "Cloudy"
        desc.contains("风") -> "Windy"
        else -> desc
    }
}

/** 3｜旅行语录：每天换一句 + 橙色脚印 */
class QuoteWidgetProvider : BaseWidget() {
    override val layout = R.layout.widget_quote

    override fun render(context: Context, v: RemoteViews, d: WidgetKit.Snapshot, c: WidgetKit.C) {
        val (quote, sign) = WidgetKit.quoteOfDay(context)
        v.setTextViewText(R.id.widget_quote_text, quote)
        v.setTextViewText(R.id.widget_quote_sign, sign)
        v.setTextColor(R.id.widget_quote_text, c.text)
        v.setTextColor(R.id.widget_quote_sign, c.muted)
        v.setImageViewBitmap(R.id.widget_quote_foot, WidgetArt.foot(c))
        WidgetKit.bindOpen(context, v, R.id.widget_root, WidgetKit.TARGET_MAP, 103)
    }
}

/** 4｜出发倒计时：大数字 + 目的地 + 小飞机 */
class DepartureWidgetProvider : BaseWidget() {
    override val layout = R.layout.widget_departure

    override fun render(context: Context, v: RemoteViews, d: WidgetKit.Snapshot, c: WidgetKit.C) {
        val plan = d.nextPlan
        if (plan == null) {
            v.setTextViewText(R.id.widget_dep_num, "—")
            v.setTextViewText(
                R.id.widget_dep_unit, WidgetKit.t(context, "暂无行程", "No trip yet")
            )
            v.setTextViewText(
                R.id.widget_dep_sub, WidgetKit.t(context, "去计划一趟旅行吧", "Plan your next trip")
            )
        } else {
            v.setTextViewText(R.id.widget_dep_num, d.daysLeft.toString())
            val dest = plan.destination.trim()
            val place = if (dest.isNotBlank()) " · $dest" else ""
            v.setTextViewText(
                R.id.widget_dep_unit,
                WidgetKit.t(context, "天后出发$place", " days to go$place")
            )
            // 第二行显示行程名（如果和地名不一样）
            val tripName = plan.name.trim()
            v.setTextViewText(
                R.id.widget_dep_sub,
                if (tripName.isNotBlank() && tripName != dest) tripName else ""
            )
        }
        v.setTextColor(R.id.widget_dep_num, c.accent)
        v.setTextColor(R.id.widget_dep_unit, c.text)
        v.setTextColor(R.id.widget_dep_sub, c.sub)
        v.setImageViewBitmap(
            R.id.widget_dep_plane,
            WidgetArt.vector(context, R.drawable.ic_widget_plane, 72, c.accentSoft)
        )
        WidgetKit.bindOpen(context, v, R.id.widget_root, WidgetKit.TARGET_PLANS, 104)
    }
}

/** 5｜已点亮城市：真实省界 + 足迹点位 */
class CityMapWidgetProvider : BaseWidget() {
    override val layout = R.layout.widget_city_map

    override fun render(context: Context, v: RemoteViews, d: WidgetKit.Snapshot, c: WidgetKit.C) {
        v.setTextViewText(R.id.widget_map_prefix, WidgetKit.t(context, "已点亮 ", "Lit up "))
        v.setTextViewText(R.id.widget_map_num, d.cityCount.toString())
        v.setTextViewText(R.id.widget_map_suffix, WidgetKit.t(context, " 城", " cities"))
        listOf(R.id.widget_map_prefix, R.id.widget_map_suffix).forEach { v.setTextColor(it, c.text) }
        v.setTextColor(R.id.widget_map_num, c.accentSoft)
        v.setImageViewBitmap(R.id.widget_map_img, WidgetArt.map(context, d.points, c))
        WidgetKit.bindOpen(context, v, R.id.widget_root, WidgetKit.TARGET_MAP, 105)
    }
}

/** 6｜本月足迹：虚线网格 + 6 根柱，本月高亮 */
class MonthlyWidgetProvider : BaseWidget() {
    override val layout = R.layout.widget_monthly

    override fun render(context: Context, v: RemoteViews, d: WidgetKit.Snapshot, c: WidgetKit.C) {
        v.setTextViewText(R.id.widget_month_prefix, WidgetKit.t(context, "本月 ", "This month "))
        v.setTextViewText(R.id.widget_month_num, d.monthCount.toString())
        v.setTextViewText(R.id.widget_month_suffix, WidgetKit.t(context, " 条足迹", " footprints"))
        v.setTextColor(R.id.widget_month_prefix, c.text)
        v.setTextColor(R.id.widget_month_num, c.accent)
        v.setTextColor(R.id.widget_month_suffix, c.text)
        v.setImageViewBitmap(
            R.id.widget_month_bars,
            WidgetArt.bars(d.monthBars.map { it.second }, c)
        )
        val labels = listOf(
            R.id.widget_month_l1, R.id.widget_month_l2, R.id.widget_month_l3,
            R.id.widget_month_l4, R.id.widget_month_l5, R.id.widget_month_l6
        )
        labels.forEachIndexed { i, id ->
            v.setTextViewText(id, d.monthBars.getOrElse(i) { "" to 0 }.first)
            v.setTextColor(id, c.muted)
        }
        WidgetKit.bindOpen(context, v, R.id.widget_root, WidgetKit.TARGET_MAP, 106)
    }
}

/** 7｜攻略推荐：圆角封面 + 想去人数 + 橙色右箭头 */
class GuideWidgetProvider : BaseWidget() {
    override val layout = R.layout.widget_guide

    override fun render(context: Context, v: RemoteViews, d: WidgetKit.Snapshot, c: WidgetKit.C) {
        val g = d.topGuide
        val en = WidgetKit.isEn(context)
        v.setTextViewText(
            R.id.widget_guide_title,
            g?.title ?: WidgetKit.t(context, "大理三天慢游攻略", "3 Slow Days in Dali")
        )
        val hot = g?.viewedCount?.takeIf { it > 0 } ?: g?.favs?.takeIf { it > 0 } ?: 32000
        v.setTextViewText(
            R.id.widget_guide_hot,
            if (en) (if (hot >= 1000) "${hot / 1000}k want to go" else "$hot want to go")
            else (if (hot >= 10000) "%.1f万人想去".format(hot / 10000.0) else "$hot 人想去")
        )
        v.setTextColor(R.id.widget_guide_title, c.text)
        v.setTextColor(R.id.widget_guide_hot, c.sub)
        v.setImageViewBitmap(
            R.id.widget_guide_cover,
            WidgetArt.roundedCover(context, null, g?.coverResId)
        )
        v.setImageViewBitmap(
            R.id.widget_guide_arrow,
            WidgetArt.vector(context, R.drawable.ic_widget_chevron, 56, c.accentSoft)
        )
        v.setOnClickPendingIntent(
            R.id.widget_root,
            WidgetKit.openPending(context, WidgetKit.TARGET_GUIDE, 107, g?.id ?: 0)
        )
    }
}

/** 8｜拍照即定位：橙色发光加号，点开直接进发布页 */
class CaptureWidgetProvider : BaseWidget() {
    override val layout = R.layout.widget_capture

    override fun render(context: Context, v: RemoteViews, d: WidgetKit.Snapshot, c: WidgetKit.C) {
        v.setTextViewText(
            R.id.widget_capture_label,
            WidgetKit.t(context, "拍照即定位", "Snap to pin a place")
        )
        v.setTextColor(R.id.widget_capture_label, c.text)
        v.setImageViewBitmap(R.id.widget_capture_plus, WidgetArt.plus(c))
        WidgetKit.bindOpen(context, v, R.id.widget_root, WidgetKit.TARGET_CAPTURE, 108)
    }
}

/**
 * 11｜课表：半透明玻璃卡（图二风格）。
 *
 * 顶部大日期（月/日，日用强调色）+ 星期与今日节数；中间只突出「正在上 / 下一节」
 * 那一节课；底部圆点表示今天有几节、当前那节高亮。卡片被拉高后才按剩余空间
 * 追加后续课程，矮卡自动收掉分隔线/教室/页脚——所以无论桌面给多大都不会裁切。
 * 单双周 / 指定周范围与 App 内网格共用 [Course.isInWeek] 判定。
 */
class CourseWidgetProvider : BaseWidget() {
    override val layout = R.layout.widget_course
    override val glass = true

    /** 拿不到桌面尺寸时的兜底：2x2 只显示一节主课 */
    override fun render(context: Context, v: RemoteViews, d: WidgetKit.Snapshot, c: WidgetKit.C) =
        renderSized(context, v, d, c, 160, 160)

    override fun renderSized(
        context: Context, v: RemoteViews, d: WidgetKit.Snapshot, c: WidgetKit.C,
        wDp: Int, hDp: Int
    ) {
        val en = WidgetKit.isEn(context)
        val t = d.courseTable
        val cal = Calendar.getInstance()
        val dowCal = cal.get(Calendar.DAY_OF_WEEK)
        // Calendar: 1=周日..7=周六 → 1=周一..7=周日
        val dayOfWeek = if (dowCal == Calendar.SUNDAY) 7 else dowCal - 1
        val week = t.currentWeek()

        // ---- 配色：RemoteViews 不走 values-en / Compose 主题，全部显式写入 ----
        intArrayOf(
            R.id.widget_course_month, R.id.widget_course_weekday,
            R.id.widget_course_primary_name,
            R.id.widget_course_more1_name, R.id.widget_course_more2_name, R.id.widget_course_more3_name
        ).forEach { v.setTextColor(it, c.text) }
        intArrayOf(
            R.id.widget_course_count, R.id.widget_course_primary_time,
            R.id.widget_course_empty
        ).forEach { v.setTextColor(it, c.sub) }
        intArrayOf(
            R.id.widget_course_primary_loc, R.id.widget_course_week,
            R.id.widget_course_more1_time, R.id.widget_course_more2_time, R.id.widget_course_more3_time,
            R.id.widget_course_more1_loc, R.id.widget_course_more2_loc, R.id.widget_course_more3_loc
        ).forEach { v.setTextColor(it, c.muted) }
        v.setTextColor(R.id.widget_course_day, c.accent)
        v.setTextColor(R.id.widget_course_status, c.accent)
        // 分隔线跟随主题：统一走 RemoteViews 已验证的 setBackgroundResource，
        // 不用 setBackgroundColor 反射（个别 ROM 不认会让整卡渲染失败）
        v.setInt(
            R.id.widget_course_divider, "setBackgroundResource",
            if (WidgetKit.isDark(context)) {
                R.drawable.widget_course_line_dark
            } else {
                R.drawable.widget_course_line
            }
        )

        // ---- 顶部：日期 / 星期 / 今日节数 ----
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        v.setTextViewText(R.id.widget_course_month, "$month/")
        v.setTextViewText(R.id.widget_course_day, "$day")
        v.setTextViewText(
            R.id.widget_course_weekday,
            WidgetKit.t(context, Course.weekDays[dayOfWeek - 1], Course.weekDaysEn[dayOfWeek - 1])
        )

        val today = d.courses
            .filter { it.dayOfWeek == dayOfWeek && it.isInWeek(week) }
            .sortedWith(compareBy({ it.startPeriod }, { it.endPeriod }))
        v.setTextViewText(
            R.id.widget_course_count,
            if (en) "${today.size} class${if (today.size == 1) "" else "es"}"
            else "${today.size} 节课"
        )
        v.setTextViewText(
            R.id.widget_course_week,
            if (en) "Week $week" else "第 $week 周"
        )

        // ---- 主推一节：正在上 > 下一节 > 今天最后一节 ----
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startOf = { course: Course -> t.slot(course.startPeriod).first }
        val endOf = { course: Course -> t.slot(course.endPeriod).second }
        val current = today.firstOrNull { nowMin in startOf(it)..endOf(it) }
        val next = today.firstOrNull { startOf(it) > nowMin }
        val primary = current ?: next ?: today.lastOrNull()
        val primaryIndex = if (primary == null) -1 else today.indexOf(primary)
        val place = primary?.let { it.location.ifBlank { it.teacher } }.orEmpty()

        if (primary == null) {
            v.setViewVisibility(R.id.widget_course_primary_block, View.GONE)
            v.setViewVisibility(R.id.widget_course_empty, View.VISIBLE)
            v.setTextViewText(
                R.id.widget_course_empty,
                WidgetKit.t(context, "今天没课，好好休息", "No classes today — enjoy!")
            )
        } else {
            v.setViewVisibility(R.id.widget_course_primary_block, View.VISIBLE)
            v.setViewVisibility(R.id.widget_course_empty, View.GONE)
            v.setTextViewText(R.id.widget_course_primary_name, primary.name)
            v.setTextViewText(
                R.id.widget_course_primary_time,
                "${t.startText(primary.startPeriod)}–${t.endText(primary.endPeriod)}"
            )
            v.setTextViewText(R.id.widget_course_primary_loc, place)
            v.setTextViewText(
                R.id.widget_course_status,
                when {
                    current != null -> WidgetKit.t(context, "正在上", "Now")
                    next != null -> WidgetKit.t(context, "下一节", "Next")
                    else -> WidgetKit.t(context, "已结束", "Done")
                }
            )
        }

        // ---- 尺寸自适应：这是「大小也合适」的关键 ----
        // 完整一屏的高度预算约 157dp（上下 padding 32 + 日期 30 + 分隔线 25 +
        // 课程名 20 + 时间 19 + 教室 17 + 页脚 14）。低于这个高度就收掉次要信息，
        // 只留日期 + 课程名（约 82dp），保证桌面缩到最小也不会裁切。
        val compact = hDp < 158
        val rows = when {
            hDp >= 300 -> 3
            hDp >= 245 -> 2
            hDp >= 195 -> 1
            else -> 0
        }
        val showLoc = wDp >= 150
        v.setViewVisibility(R.id.widget_course_divider, if (compact) View.GONE else View.VISIBLE)
        v.setViewVisibility(R.id.widget_course_footer, if (compact) View.GONE else View.VISIBLE)
        v.setViewVisibility(R.id.widget_course_primary_time, if (compact) View.GONE else View.VISIBLE)
        v.setViewVisibility(
            R.id.widget_course_primary_loc,
            if (compact || !showLoc || place.isBlank()) View.GONE else View.VISIBLE
        )

        // 后续课程：优先列主课之后的，不足再补之前的，避免已上完的课占掉首位
        val rest = if (primaryIndex < 0) emptyList()
        else today.drop(primaryIndex + 1) + today.take(primaryIndex)
        val moreRows = listOf(
            Triple(R.id.widget_course_more1, R.id.widget_course_more1_time, R.id.widget_course_more1_name),
            Triple(R.id.widget_course_more2, R.id.widget_course_more2_time, R.id.widget_course_more2_name),
            Triple(R.id.widget_course_more3, R.id.widget_course_more3_time, R.id.widget_course_more3_name)
        )
        val moreLocs = intArrayOf(
            R.id.widget_course_more1_loc, R.id.widget_course_more2_loc, R.id.widget_course_more3_loc
        )
        for (i in moreRows.indices) {
            val (rowId, timeId, nameId) = moreRows[i]
            val course = rest.getOrNull(i)
            val visible = course != null && i < rows && !compact
            v.setViewVisibility(rowId, if (visible) View.VISIBLE else View.GONE)
            if (course != null && visible) {
                v.setTextViewText(timeId, t.startText(course.startPeriod))
                v.setTextViewText(nameId, course.name)
                v.setViewVisibility(moreLocs[i], if (showLoc) View.VISIBLE else View.GONE)
                if (showLoc) {
                    v.setTextViewText(moreLocs[i], course.location.ifBlank { course.teacher })
                }
            }
        }

        // ---- 底部圆点：今天有几节课，主课那颗高亮 ----
        val dotIds = intArrayOf(
            R.id.widget_course_dot1, R.id.widget_course_dot2, R.id.widget_course_dot3,
            R.id.widget_course_dot4, R.id.widget_course_dot5
        )
        val mutedDot = if (WidgetKit.isDark(context)) {
            R.drawable.widget_course_dot_muted
        } else {
            R.drawable.widget_course_dot_muted_light
        }
        val activeDot = if (primaryIndex < 0) -1 else minOf(primaryIndex, dotIds.lastIndex)
        for (i in dotIds.indices) {
            val visible = !compact
            v.setViewVisibility(dotIds[i], if (visible) View.VISIBLE else View.GONE)
            if (visible) {
                // 固定 5 颗（图二那种分页点）：当前那节高亮，其余淡；今天没课就全淡
                v.setInt(
                    dotIds[i], "setBackgroundResource",
                    if (i == activeDot) R.drawable.widget_dot else mutedDot
                )
            }
        }

        // 点卡片直接进课表页（原来绕到「我的计划」）
        WidgetKit.bindOpen(context, v, R.id.widget_root, WidgetKit.TARGET_COURSE, 109)
    }
}
