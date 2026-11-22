package com.example.my.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import com.example.my.model.Packing
import com.example.my.model.TripPlan
import com.example.my.ui.theme.Bg
import com.example.my.ui.theme.Brand
import com.example.my.ui.theme.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * 行程长图渲染器（与年报长图一样用 android.graphics.Canvas，零新增依赖）。
 * 支持单条与批量：批量导出时把选中的所有行程从上到下排在一张长图里。
 */
object PlanPoster {

    private const val W = 1080
    /** 1080 宽对应 360dp 设计稿，即 1dp = 3px */
    private const val S = 3f
    private const val PAD = 20f * S
    private const val CARD_PAD = 16f * S
    private const val MAX_H = 12000
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun render(plans: List<TripPlan>, today: Long): Bitmap {
        val cardW = (W - PAD * 2).toInt()
        val textW = (cardW - CARD_PAD * 2).toInt()

        // 先量一遍高度：Canvas 绘制无法中途扩容，必须一次开够
        val heights = plans.map { cardHeight(it, textW) }
        val headerH = 30f * S + 50f * S + 24f * S + 16f * S
        val footerH = 18f * S + 20f * S
        val total = (PAD + headerH + heights.sum() + 12f * S * max(0, plans.size - 1) + footerH + PAD)
            .toInt().coerceIn(600, MAX_H)

        val bitmap = Bitmap.createBitmap(W, total, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Bg.Background.toArgb())

        var y = PAD
        // ---------- 页头 ----------
        val brandPaint = textPaint(12f, Brand.Terracotta.toArgb(), bold = true)
        canvas.drawText("MY 足迹 · 出行计划", PAD, y + 14f * S, brandPaint)
        y += 30f * S
        val titlePaint = textPaint(28f, Text.Primary.toArgb(), bold = true)
        canvas.drawText("我的行程", PAD, y + 30f * S, titlePaint)
        y += 50f * S
        val subPaint = textPaint(11f, Text.Muted.toArgb())
        val sub = "共 ${plans.size} 段旅程 · 导出于 " +
            SimpleDateFormat("yyyy.MM.dd", Locale.CHINA).format(Date(System.currentTimeMillis()))
        canvas.drawText(sub, PAD, y + 12f * S, subPaint)
        y += 24f * S + 16f * S

        // ---------- 行程卡片 ----------
        plans.forEachIndexed { index, plan ->
            val h = heights[index].toFloat()
            drawCard(canvas, plan, PAD, y, cardW, h, textW, today)
            y += h + if (index == plans.lastIndex) 0f else 12f * S
        }

        // ---------- 页脚 ----------
        val footerPaint = textPaint(10f, Text.Muted.toArgb())
        val footer = "由 MY足迹 生成 · 记录每一段旅程"
        val fw = footerPaint.measureText(footer)
        canvas.drawText(footer, (W - fw) / 2f, total - PAD - 4f * S, footerPaint)
        return bitmap
    }

    // ---------- 卡片绘制 ----------

    private fun drawCard(
        canvas: Canvas,
        plan: TripPlan,
        x: Float,
        top: Float,
        w: Int,
        h: Float,
        textW: Int,
        today: Long
    ) {
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Bg.Surface.toArgb() }
        canvas.drawRoundRect(RectF(x, top, x + w, top + h), 20f * S, 20f * S, cardPaint)

        val left = x + CARD_PAD
        var y = top + CARD_PAD

        // 标题 + 状态胶囊
        val namePaint = textPaint(18f, Text.Primary.toArgb(), bold = true)
        val status = statusOf(plan, today)
        val statusPaint = textPaint(10f, status.second, bold = true)
        val pillW = statusPaint.measureText(status.first) + 20f * S
        val pillH = 22f * S
        val nameLayout = staticLayout(plan.name, namePaint, (textW - pillW - 12f * S).toInt())
        canvas.save()
        canvas.translate(left, y)
        nameLayout.draw(canvas)
        canvas.restore()

        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = status.second
            alpha = 26
        }
        val pillLeft = x + w - CARD_PAD - pillW
        canvas.drawRoundRect(
            RectF(pillLeft, y + 4f * S, pillLeft + pillW, y + 4f * S + pillH),
            pillH / 2f, pillH / 2f, pillPaint
        )
        canvas.drawText(
            status.first,
            pillLeft + 10f * S,
            y + 4f * S + pillH / 2f + 4f * S,
            statusPaint
        )
        y += max(nameLayout.height.toFloat(), pillH + 8f * S) + 8f * S

        // 目的地 + 日期
        val metaPaint = textPaint(11f, Text.Secondary.toArgb())
        val metaLayout = staticLayout(metaText(plan), metaPaint, textW)
        canvas.save()
        canvas.translate(left, y)
        metaLayout.draw(canvas)
        canvas.restore()
        y += metaLayout.height + 10f * S

        // 同行人
        if (plan.companions.isNotEmpty()) {
            val compPaint = textPaint(11f, Text.Secondary.toArgb())
            val compLayout = staticLayout("同行人：${plan.companions.joinToString("、")}", compPaint, textW)
            canvas.save()
            canvas.translate(left, y)
            compLayout.draw(canvas)
            canvas.restore()
            y += compLayout.height + 8f * S
        }

        // 每日安排（最多 8 条，防止长图无限拉长）
        if (plan.dailyPlan.isNotEmpty()) {
            val dayPaint = textPaint(11f, Text.Secondary.toArgb())
            val body = plan.dailyPlan.take(8).joinToString("\n") { "· $it" }
            val dayLayout = staticLayout(body, dayPaint, textW)
            canvas.save()
            canvas.translate(left, y)
            dayLayout.draw(canvas)
            canvas.restore()
            y += dayLayout.height + 8f * S
        }

        // 备注
        if (plan.note.isNotBlank()) {
            val notePaint = textPaint(11f, Text.Muted.toArgb())
            val noteLayout = staticLayout(plan.note, notePaint, textW, maxLines = 3)
            canvas.save()
            canvas.translate(left, y)
            noteLayout.draw(canvas)
            canvas.restore()
            y += noteLayout.height + 8f * S
        }

        // 清单进度
        val all = Packing.allKeys(plan.customPacking)
        val checked = all.count { it in plan.packedItems }
        val packPaint = textPaint(11f, Brand.Terracotta.toArgb())
        canvas.drawText("出行清单已准备 $checked / ${all.size}", left, top + h - CARD_PAD - 4f * S, packPaint)
    }

    private fun cardHeight(plan: TripPlan, textW: Int): Float {
        var h = CARD_PAD * 2
        val namePaint = textPaint(18f, Text.Primary.toArgb(), bold = true)
        val statusPaint = textPaint(10f, Text.Primary.toArgb(), bold = true)
        val pillW = statusPaint.measureText("已完成") + 20f * S
        val nameLayout = staticLayout(plan.name, namePaint, (textW - pillW - 12f * S).toInt())
        h += max(nameLayout.height.toFloat(), 30f * S) + 8f * S

        val metaPaint = textPaint(11f, Text.Secondary.toArgb())
        h += staticLayout(metaText(plan), metaPaint, textW).height + 10f * S
        if (plan.companions.isNotEmpty()) {
            h += staticLayout("同行人：${plan.companions.joinToString("、")}", metaPaint, textW).height + 8f * S
        }
        if (plan.dailyPlan.isNotEmpty()) {
            h += staticLayout(plan.dailyPlan.take(8).joinToString("\n") { "· $it" }, metaPaint, textW).height + 8f * S
        }
        if (plan.note.isNotBlank()) {
            h += staticLayout(plan.note, textPaint(11f, Text.Muted.toArgb()), textW, maxLines = 3).height + 8f * S
        }
        h += 22f * S
        return h
    }

    // ---------- 工具 ----------

    private fun textPaint(sp: Float, color: Int, bold: Boolean = false): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sp * S
            this.color = color
            typeface = if (bold) Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            else Typeface.DEFAULT
        }

    private fun staticLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        maxLines: Int = Int.MAX_VALUE
    ): StaticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .setLineSpacing(0f, 1.35f)
        .setMaxLines(maxLines)
        .build()

    /** 待出发 / 进行中 / 已完成 + 对应颜色 */
    private fun statusOf(plan: TripPlan, today: Long): Pair<String, Int> {
        val start = startOf(plan) ?: return "待出发" to Brand.Terracotta.toArgb()
        val end = start + (plan.days - 1).coerceAtLeast(0) * DAY_MS
        return when {
            start > today -> "待出发" to Brand.Terracotta.toArgb()
            today <= end -> "进行中" to 0xFF2E9E6B.toInt()
            else -> "已完成" to Text.Muted.toArgb()
        }
    }

    private fun startOf(plan: TripPlan): Long? = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(plan.startDate)?.time
    }.getOrNull()

    private fun metaText(plan: TripPlan): String = buildString {
        append(plan.destination.ifBlank { "目的地待定" })
        append(" · ").append(dateRange(plan))
        append(" · ").append(plan.days).append(" 天")
    }

    private fun dateRange(plan: TripPlan): String {
        val start = startOf(plan) ?: return plan.startDate
        val end = start + (plan.days - 1).coerceAtLeast(0) * DAY_MS
        val fmt = SimpleDateFormat("yyyy.M.d", Locale.CHINA)
        return "${fmt.format(Date(start))} - ${fmt.format(Date(end))}"
    }
}
