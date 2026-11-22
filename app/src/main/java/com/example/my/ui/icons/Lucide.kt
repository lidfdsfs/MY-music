package com.example.my.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 描边图标集（24×24 / stroke 2 / round cap+join）。
 *
 * 路径数据取自 Lucide 官方 SVG —— 也就是 morphicons 内置的那套描边图标
 * （morphicons 本身是 Web 端的图标形变库，不提供 Android Compose 资源）。
 * 这里把 SVG 的 path 数据直接解析成 Compose 的 ImageVector，零新增依赖，
 * 颜色交给 Icon(tint = ...) 统一着色，因此同一份图标可用于任何语义色。
 * 许可：Lucide 为 ISC，全文见 assets/licenses/lucide-ISC.txt。
 */
object Lucide {

    val Sparkles: ImageVector by lazy {
        icon("Sparkles") {
            stroke(
                "M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z",
                "M20 2v4", "M22 4h-4"
            )
            strokeCircle(4f, 20f, 2f)
        }
    }

    val ImagePlus: ImageVector by lazy {
        icon("ImagePlus") {
            stroke(
                "M16 5h6", "M19 2v6",
                "M21 11.5V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7.5",
                "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"
            )
            strokeCircle(9f, 9f, 2f)
        }
    }

    val Send: ImageVector by lazy {
        icon("Send") {
            stroke(
                "M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z",
                "m21.854 2.147-10.94 10.939"
            )
        }
    }

    val Trash: ImageVector by lazy {
        icon("Trash2") {
            stroke(
                "M10 11v6", "M14 11v6",
                "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
                "M3 6h18",
                "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"
            )
        }
    }

    val Settings2: ImageVector by lazy {
        icon("Settings2") {
            stroke("M14 17H5", "M19 7h-9")
            strokeCircle(17f, 17f, 3f)
            strokeCircle(7f, 7f, 3f)
        }
    }

    val History: ImageVector by lazy {
        icon("History") {
            stroke(
                "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
                "M3 3v5h5",
                "M12 7v5l4 2"
            )
        }
    }

    val ChartColumn: ImageVector by lazy {
        icon("ChartColumn") {
            stroke(
                "M3 3v16a2 2 0 0 0 2 2h16",
                "M18 17V9", "M13 17V5", "M8 17v-3"
            )
        }
    }

    val Mic: ImageVector by lazy {
        icon("Mic") {
            stroke("M12 19v3", "M19 10v2a7 7 0 0 1-14 0v-2")
            strokeRoundRect(9f, 2f, 6f, 13f, 3f)
        }
    }

    val Plus: ImageVector by lazy {
        icon("Plus") { stroke("M5 12h14", "M12 5v14") }
    }

    val Close: ImageVector by lazy {
        icon("X") { stroke("M18 6 6 18", "m6 6 12 12") }
    }

    val Copy: ImageVector by lazy {
        icon("Copy") {
            stroke("M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1")
            strokeRoundRect(9f, 9f, 13f, 13f, 2f)
        }
    }

    val Camera: ImageVector by lazy {
        icon("Camera") {
            stroke("M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z")
            strokeCircle(12f, 13f, 3f)
        }
    }

    val Languages: ImageVector by lazy {
        icon("Languages") {
            stroke(
                "m5 8 6 6", "m4 14 6-6 2-3", "M2 5h12", "M7 2h1",
                "m22 22-2-4.5", "m16.5 22 2-4.5", "M9.5 14l2-3.5 2 3.5",
                "M14 14h2", "M2 14h5"
            )
        }
    }

    val AlignLeft: ImageVector by lazy {
        icon("AlignLeft") { stroke("M21 6H3", "M15 12H3", "M17 18H3") }
    }

    val PenLine: ImageVector by lazy {
        icon("PenLine") {
            stroke("M12 20h9", "M16.376 3.622a1 1 0 0 1 3 3L7 19l-4 1 1-4Z")
        }
    }

    val Wand2: ImageVector by lazy {
        icon("Wand2") {
            stroke(
                "m21.64 3.64-1.28-1.28a1.21 1.21 0 0 0-1.72 0L14 7",
                "m14 7 3 3 4.36-4.36a1.21 1.21 0 0 0 0-1.72Z",
                "m18 11-3-3", "M3 21l9-9"
            )
        }
    }

    val MessageSquarePlus: ImageVector by lazy {
        icon("MessageSquarePlus") {
            stroke(
                "M22 17a2 2 0 0 1-2 2H6.828a2 2 0 0 0-1.414.586l-2.202 2.202A.71.71 0 0 1 2 21.286V5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2z",
                "M12 8v6", "M9 11h6"
            )
        }
    }

    val KeyRound: ImageVector by lazy {
        icon("KeyRound") {
            stroke("M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z")
            fillCircle(16.5f, 7.5f, 1.1f)
        }
    }

    val Gauge: ImageVector by lazy {
        icon("Gauge") {
            stroke("m12 14 4-4", "M3.34 19a10 10 0 1 1 17.32 0")
        }
    }

    val ChevronLeft: ImageVector by lazy {
        icon("ChevronLeft") { stroke("m15 18-6-6 6-6") }
    }

    val CheckSquare: ImageVector by lazy {
        icon("SquareCheckBig") {
            stroke(
                "M21 10.656V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h12.344",
                "m9 11 3 3L22 4"
            )
        }
    }

    val ArrowUp: ImageVector by lazy {
        icon("ArrowUp") { stroke("m5 12 7-7 7 7", "M12 19V5") }
    }

    val Zap: ImageVector by lazy {
        icon("Zap") {
            stroke("M15.914 4a1.5 1.5 0 00-2.474-1.561l-9 9A1.5 1.5 0 005.5 14h4.002a.5.5 0 01.471.666L8.086 20a1.5 1.5 0 002.475 1.56l9-9A1.5 1.5 0 0018.5 10h-3.997a.5.5 0 01-.472-.667z")
        }
    }

    val SkipBack: ImageVector by lazy {
        icon("SkipBack") {
            stroke("M19 20L9 12L19 4V20Z", "M5 19V5")
        }
    }

    val SkipForward: ImageVector by lazy {
        icon("SkipForward") {
            stroke("M5 4L15 12L5 20V4Z", "M19 5V19")
        }
    }

    val ListMusic: ImageVector by lazy {
        icon("ListMusic") {
            stroke("M21 15V6", "M12 12H3", "M16 6H3", "M12 18H3")
            strokeCircle(18.5f, 15.5f, 2.5f)
        }
    }

    val AudioLines: ImageVector by lazy {
        icon("AudioLines") {
            stroke("M2 10v3", "M6 6v11", "M10 3v18", "M14 8v7", "M18 5v13", "M22 10v3")
        }
    }

    val Repeat2: ImageVector by lazy {
        icon("Repeat2") {
            stroke(
                "m17 2 4 4-4 4",
                "M3 11v-1a4 4 0 0 1 4-4h14",
                "m7 22-4-4 4-4",
                "M21 13v1a4 4 0 0 1-4 4H3"
            )
        }
    }

    val Shuffle: ImageVector by lazy {
        icon("Shuffle") {
            stroke(
                "m18 14 4 4-4 4",
                "m18 2 4 4-4 4",
                "M2 18h1.4c1.3 0 2.5-.6 3.3-1.7l6.1-8.6c.8-1.1 2-1.7 3.3-1.7H22",
                "M2 6h1.4c1.3 0 2.5.6 3.3 1.7l6.1 8.6c.8 1.1 2 1.7 3.3 1.7H22"
            )
        }
    }

    val Heart: ImageVector by lazy {
        icon("Heart") {
            stroke("M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z")
        }
    }

    val Share2: ImageVector by lazy {
        icon("Share2") {
            strokeCircle(18f, 5f, 3f)
            strokeCircle(6f, 12f, 3f)
            strokeCircle(18f, 19f, 3f)
            stroke("m8.59 13.51 6.83 3.98", "m15.41 6.51-6.82 3.98")
        }
    }

    val MessageSquare: ImageVector by lazy {
        icon("MessageSquare") {
            stroke("M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z")
        }
    }

    val Search: ImageVector by lazy {
        icon("Search") {
            strokeCircle(11f, 11f, 8f)
            stroke("m21 21-4.3-4.3")
        }
    }

    val EllipsisVertical: ImageVector by lazy {
        icon("EllipsisVertical") {
            fillCircle(12f, 12f, 1f)
            fillCircle(12f, 5f, 1f)
            fillCircle(12f, 19f, 1f)
        }
    }

    val Download: ImageVector by lazy {
        icon("Download") {
            stroke("M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m7 10 5 5 5-5", "M12 15V3")
        }
    }

    // ---------- 构建辅助 ----------

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()

    /** 一组共用描边样式的 path（颜色留空，由 Icon 的 tint 着色） */
    private fun ImageVector.Builder.stroke(vararg paths: String) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            paths.forEach { parseSvgPath(it, this) }
        }
    }

    private fun ImageVector.Builder.strokeCircle(cx: Float, cy: Float, r: Float) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(cx - r, cy)
            arcTo(r, r, 0f, false, true, cx + r, cy)
            arcTo(r, r, 0f, false, true, cx - r, cy)
            close()
        }
    }

    private fun ImageVector.Builder.fillCircle(cx: Float, cy: Float, r: Float) {
        path(fill = SolidColor(Color.Black), stroke = null) {
            moveTo(cx - r, cy)
            arcTo(r, r, 0f, false, true, cx + r, cy)
            arcTo(r, r, 0f, false, true, cx - r, cy)
            close()
        }
    }

    private fun ImageVector.Builder.strokeRoundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(x + r, y)
            lineTo(x + w - r, y)
            arcTo(r, r, 0f, false, true, x + w, y + r)
            lineTo(x + w, y + h - r)
            arcTo(r, r, 0f, false, true, x + w - r, y + h)
            lineTo(x + r, y + h)
            arcTo(r, r, 0f, false, true, x, y + h - r)
            lineTo(x, y + r)
            arcTo(r, r, 0f, false, true, x + r, y)
            close()
        }
    }
}

/**
 * 极简 SVG path 解析器，覆盖 Lucide 用到的全部指令：
 * M/m L/l H/h V/v C/c S/s Q/q T/t A/a Z/z。
 * 只做「把 SVG 路经搬进 Compose PathBuilder」这一件事，不做任何归一化，
 * 因此图标与 Lucide 官方渲染逐像素一致。
 */
private fun parseSvgPath(d: String, pb: PathBuilder) {
    var i = 0
    val n = d.length
    var cmd = ' '
    var prev = ' '
    var cx = 0f
    var cy = 0f
    var sx = 0f
    var sy = 0f
    var lastCx = 0f
    var lastCy = 0f

    fun skipSep() {
        while (i < n) {
            val ch = d[i]
            if (ch == ' ' || ch == ',' || ch == '\n' || ch == '\t' || ch == '\r') i++ else break
        }
    }

    fun nextNum(): Float {
        skipSep()
        val start = i
        if (i < n && (d[i] == '-' || d[i] == '+')) i++
        while (i < n && d[i].isDigit()) i++
        if (i < n && d[i] == '.') {
            i++
            while (i < n && d[i].isDigit()) i++
        }
        if (i < n && (d[i] == 'e' || d[i] == 'E')) {
            i++
            if (i < n && (d[i] == '-' || d[i] == '+')) i++
            while (i < n && d[i].isDigit()) i++
        }
        return if (i > start) d.substring(start, i).toFloatOrNull() ?: 0f else 0f
    }

    fun nextFlag(): Boolean {
        skipSep()
        if (i >= n) return false
        val c = d[i]
        i++
        return c == '1'
    }

    while (i < n) {
        skipSep()
        if (i >= n) break
        val loopStart = i
        if (d[i].isLetter()) {
            cmd = d[i]
            i++
        }
        // 相对指令需要在处理前先拿到基准点
        when (cmd) {
            'M' -> {
                cx = nextNum(); cy = nextNum()
                pb.moveTo(cx, cy); sx = cx; sy = cy
                cmd = 'L'
            }
            'm' -> {
                cx += nextNum(); cy += nextNum()
                pb.moveTo(cx, cy); sx = cx; sy = cy
                cmd = 'l'
            }
            'L' -> { cx = nextNum(); cy = nextNum(); pb.lineTo(cx, cy) }
            'l' -> { cx += nextNum(); cy += nextNum(); pb.lineTo(cx, cy) }
            'H' -> { cx = nextNum(); pb.lineTo(cx, cy) }
            'h' -> { cx += nextNum(); pb.lineTo(cx, cy) }
            'V' -> { cy = nextNum(); pb.lineTo(cx, cy) }
            'v' -> { cy += nextNum(); pb.lineTo(cx, cy) }
            'C' -> {
                val x1 = nextNum(); val y1 = nextNum()
                val x2 = nextNum(); val y2 = nextNum()
                val x = nextNum(); val y = nextNum()
                pb.curveTo(x1, y1, x2, y2, x, y)
                lastCx = x2; lastCy = y2; cx = x; cy = y
            }
            'c' -> {
                val x1 = cx + nextNum(); val y1 = cy + nextNum()
                val x2 = cx + nextNum(); val y2 = cy + nextNum()
                val x = cx + nextNum(); val y = cy + nextNum()
                pb.curveTo(x1, y1, x2, y2, x, y)
                lastCx = x2; lastCy = y2; cx = x; cy = y
            }
            'S' -> {
                val x1 = if (prev == 'C' || prev == 'c' || prev == 'S' || prev == 's') 2 * cx - lastCx else cx
                val y1 = if (prev == 'C' || prev == 'c' || prev == 'S' || prev == 's') 2 * cy - lastCy else cy
                val x2 = nextNum(); val y2 = nextNum()
                val x = nextNum(); val y = nextNum()
                pb.curveTo(x1, y1, x2, y2, x, y)
                lastCx = x2; lastCy = y2; cx = x; cy = y
            }
            's' -> {
                val x1 = if (prev == 'C' || prev == 'c' || prev == 'S' || prev == 's') 2 * cx - lastCx else cx
                val y1 = if (prev == 'C' || prev == 'c' || prev == 'S' || prev == 's') 2 * cy - lastCy else cy
                val x2 = cx + nextNum(); val y2 = cy + nextNum()
                val x = cx + nextNum(); val y = cy + nextNum()
                pb.curveTo(x1, y1, x2, y2, x, y)
                lastCx = x2; lastCy = y2; cx = x; cy = y
            }
            'Q' -> {
                val x1 = nextNum(); val y1 = nextNum()
                val x = nextNum(); val y = nextNum()
                pb.quadTo(x1, y1, x, y)
                lastCx = x1; lastCy = y1; cx = x; cy = y
            }
            'q' -> {
                val x1 = cx + nextNum(); val y1 = cy + nextNum()
                val x = cx + nextNum(); val y = cy + nextNum()
                pb.quadTo(x1, y1, x, y)
                lastCx = x1; lastCy = y1; cx = x; cy = y
            }
            'T' -> {
                val x1 = if (prev == 'Q' || prev == 'q' || prev == 'T' || prev == 't') 2 * cx - lastCx else cx
                val y1 = if (prev == 'Q' || prev == 'q' || prev == 'T' || prev == 't') 2 * cy - lastCy else cy
                val x = nextNum(); val y = nextNum()
                pb.quadTo(x1, y1, x, y)
                lastCx = x1; lastCy = y1; cx = x; cy = y
            }
            't' -> {
                val x1 = if (prev == 'Q' || prev == 'q' || prev == 'T' || prev == 't') 2 * cx - lastCx else cx
                val y1 = if (prev == 'Q' || prev == 'q' || prev == 'T' || prev == 't') 2 * cy - lastCy else cy
                val x = cx + nextNum(); val y = cy + nextNum()
                pb.quadTo(x1, y1, x, y)
                lastCx = x1; lastCy = y1; cx = x; cy = y
            }
            'A' -> {
                val rx = nextNum(); val ry = nextNum(); val rot = nextNum()
                val large = nextFlag(); val sweep = nextFlag()
                val x = nextNum(); val y = nextNum()
                pb.arcTo(rx, ry, rot, large, sweep, x, y)
                cx = x; cy = y
            }
            'a' -> {
                val rx = nextNum(); val ry = nextNum(); val rot = nextNum()
                val large = nextFlag(); val sweep = nextFlag()
                val x = cx + nextNum(); val y = cy + nextNum()
                pb.arcTo(rx, ry, rot, large, sweep, x, y)
                cx = x; cy = y
            }
            'Z', 'z' -> {
                pb.close()
                cx = sx; cy = sy
                cmd = ' '
            }
            else -> i++ // 未知指令，跳过避免死循环
        }
        prev = if (cmd == 'M') 'L' else cmd
        // 防御：任何情况下都没能推进游标就强制前进一格，避免畸形数据把界面卡死
        if (i == loopStart) i++
    }
}
