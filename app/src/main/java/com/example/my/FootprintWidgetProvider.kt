package com.example.my

import android.content.Context
import android.widget.RemoteViews
import com.example.my.widget.BaseWidget
import com.example.my.widget.WidgetKit

/**
 * 原有的「足迹统计」小组件：保留入口，外观与数据层统一到新版卡片体系。
 * 数据走 [WidgetKit.load]，双语与浅/深主题由公共层解析。
 */
class FootprintWidgetProvider : BaseWidget() {

    override val layout = R.layout.widget_footprint

    override fun render(
        context: Context, v: RemoteViews, d: WidgetKit.Snapshot, c: WidgetKit.C
    ) {
        val total = d.footprints.size
        v.setTextViewText(R.id.widget_title, WidgetKit.t(context, "足迹统计", "Footprint stats"))
        v.setTextViewText(R.id.widget_count, total.toString())
        v.setTextViewText(
            R.id.widget_cities,
            WidgetKit.t(
                context,
                "${d.cityCount} 城 · $total 条足迹",
                "${d.cityCount} cities · $total footprints"
            )
        )
        val recent = d.footprints.maxByOrNull { WidgetKit.footprintMillis(it) }
        v.setTextViewText(
            R.id.widget_recent,
            if (recent != null && recent.city.isNotBlank())
                WidgetKit.t(context, "最近：${recent.city}", "Latest: ${recent.city}")
            else WidgetKit.t(context, "还没有足迹，点这里开始记录", "No footprints yet — tap to start")
        )
        v.setTextColor(R.id.widget_title, c.text)
        v.setTextColor(R.id.widget_count, c.accent)
        v.setTextColor(R.id.widget_cities, c.sub)
        v.setTextColor(R.id.widget_recent, c.muted)
        WidgetKit.bindOpen(context, v, R.id.widget_root, WidgetKit.TARGET_MAP, 109)
    }

    companion object {
        fun refresh(context: Context) {
            WidgetKit.refresh(context, FootprintWidgetProvider::class.java)
        }
    }
}
