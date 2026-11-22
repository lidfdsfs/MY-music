package com.example.my.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.example.my.data.AiApi
import com.example.my.data.AiPresets
import com.example.my.model.Course
import com.example.my.util.PhotoStore
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 课表截图 / 拍照 → 课程列表。
 *
 * 走的是完全离线的 MLKit 中文识别（模型已打进 APK，不依赖 Google Play 服务），
 * 再用文字框的几何位置还原「星期 × 节次」二维网格：
 *  1. 找表头「周一…周日」定列；
 *  2. 找左侧「第 N 节 / 1 2 3」定行；
 *  3. 每个格子里的多行文字归并成一门课（首行课程名，其余按关键词判老师 / 地点）；
 *  4. 连续两格的同名课合并成跨节次。
 * 找不到表头或行号时退回「均分 7 列 + 按行聚类」，保证再差的截图也能出结果，
 * 剩下的交给用户在预览里改。
 */
object CourseImporter {

    /** 自带的矩形，避免几何逻辑依赖 android.graphics.Rect（JVM 单测里 stub 会抛异常） */
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun centerX() = (left + right) / 2
        fun centerY() = (top + bottom) / 2
        fun width() = right - left
        fun height() = bottom - top
    }

    data class Line(val text: String, val rect: Box)

    /** [courses] 已去重合并；[note] 是给用户看的一句识别说明 */
    data class Report(
        val courses: List<Course>,
        val lineCount: Int,
        val note: String
    )

    private val HEADER_RE = Regex("^(?:周|星期|礼拜)([一二三四五六日天])$")
    private val PERIOD_RE = Regex("^第?\\s*([0-9一二三四五六七八九十]{1,3})\\s*节?$")
    private val DAY_OF = mapOf(
        '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '日' to 7, '天' to 7
    )
    private val CN_NUM = mapOf(
        '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6,
        '七' to 7, '八' to 8, '九' to 9
    )

    /** OCR 常见的课程名后缀：周次 / 节次备注，保留会把同名课判成两门 */
    private val NAME_NOTE = Regex("(?:[（(][^）)]*(?:周|节|单双)[^）)]*[）)]|[\\[【][^\\]】]*(?:周|节)[^\\]】]*[\\]】])")

    /** 表头 / 页脚之类不参与课程解析的行 */
    private val NOISE = listOf(
        "课程表", "课表", "作息", "时间表", "节次", "上课时间", "打印", "导出",
        "学号", "姓名", "学期", "专业", "班级", "备注", "说明", "上午", "下午", "晚上", "中午",
        "午休", "晚休", "课间", "早自习"
    )

    // ---------- 左侧时间栏 / 表头日期这类"非课程内容" ----------
    /** 纯时间：「08:20」「8:20-9:05」，左侧节次时间栏经常被误当课程名 */
    private val TIME_ONLY = Regex("^\\d{1,2}:\\d{2}(?::\\d{2})?(?:-{1,2}\\d{1,2}:\\d{2})?$")
    /** 纯日期：「11/9」「2026-11-09」「2026年11月9日起」「11月9日」 */
    private val DATE_ONLY = Regex(
        "^(?:\\d{4}年)?\\d{1,2}月\\d{1,2}日(?:起)?$|^\\d{1,2}[/.]\\d{1,2}$|^\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}$"
    )
    /** 纯周次：「10周」「第10周」「1-16周」「第3-5周(单)」 */
    private val WEEK_ONLY = Regex("^第?\\d{1,3}(?:-\\d{1,3})?周(?:[（(][^）)]*[）)])?$")
    /** 纯数字：节次号、教室号 */
    private val DIGITS_ONLY = Regex("^\\d{1,6}$")

    /** 时间、日期、周次、纯数字都不是课程内容，识别进格子会变成假课程 */
    internal fun isNonCourseText(compact: String): Boolean =
        TIME_ONLY.matches(compact) || DATE_ONLY.matches(compact) ||
            WEEK_ONLY.matches(compact) || DIGITS_ONLY.matches(compact)

    suspend fun fromUri(
        context: Context,
        uri: Uri,
        ocrEndpoint: AiPresets.OcrEndpoint = AiPresets.OcrEndpoint()
    ): Report = withContext(Dispatchers.IO) {
        val bmp = runCatching { PhotoStore.decodeUri(context, uri, 1800) }.getOrNull()
            ?: return@withContext Report(emptyList(), 0, "图片读取失败，换一张试试")
        try {
            recognize(bmp, ocrEndpoint)
        } finally {
            runCatching { bmp.recycle() }
        }
    }

    private suspend fun recognize(bitmap: Bitmap, endpoint: AiPresets.OcrEndpoint): Report {
        // 优先用用户在「模型管理 - OCR」接入的识别端点：让模型直接输出结构化 JSON，
        // 星期 / 节次 / 课名 / 老师 / 教室一步到位，不再依赖文字坐标猜网格；
        // 未接入、返回空或解析失败时，回退端侧 MLKit 几何识别。
        if (endpoint.ready) {
            val structured = remoteStructured(bitmap, endpoint)
            if (structured.courses.isNotEmpty()) return structured
        }
        val lines = localLines(bitmap)
        if (lines.isEmpty()) return Report(emptyList(), 0, "没识别到文字，图片可能太糊或角度太大")
        return parseLines(lines)
    }

    private suspend fun localLines(bitmap: Bitmap): List<Line> {
        val client = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        val text = try {
            Tasks.await(client.process(InputImage.fromBitmap(bitmap, 0)), 30, TimeUnit.SECONDS)
        } finally {
            runCatching { client.close() }
        }
        return text.textBlocks.flatMap { it.lines }.mapNotNull { l ->
            val box = l.boundingBox ?: return@mapNotNull null
            val t = l.text.replace('\u3000', ' ').trim()
            if (t.isBlank() || box.height() < 8) null
            else Line(t, Box(box.left, box.top, box.right, box.bottom))
        }
    }

    /** 云端 OCR：要求模型直接输出结构化 JSON 数组，解析后一步到位落网格 */
    private suspend fun remoteStructured(bitmap: Bitmap, endpoint: AiPresets.OcrEndpoint): Report {
        val bytes = java.io.ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            out.toByteArray()
        }
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val prompt = """识别这张课表截图中的课程，只输出一个 JSON 数组，不要解释、不要 markdown、不要代码块围栏。
数组每个元素包含字段：
"day": 星期几数字，1=周一，2=周二，…，7=周日；
"startPeriod": 开始节次（数字，第 1 节记 1）；
"endPeriod": 结束节次（跨多节填结束节次，单节与 startPeriod 相同）；
"name": 课程名（去掉周次、单双周等括号备注）；
"teacher": 老师姓名，没有就填 ""；
"location": 教室或上课地点，没有就填 ""。
只列真实课程；表头星期、左侧节次号、时间、午休、说明文字都不要列入。"""
        val text = AiApi.ocrRemote(endpoint, base64, "image/jpeg", prompt, 4096)
            ?: return Report(emptyList(), 0, "")
        val arr = extractJsonArray(text) ?: return Report(emptyList(), 0, "")

        val raw = mutableListOf<Course>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val day = parseDay(o.optString("day", "")) ?: continue
            val sp = periodLoose(o.optString("startPeriod", "")) ?: continue
            val ep = periodLoose(o.optString("endPeriod", "")) ?: sp
            if (sp !in 1..16) continue
            val name = cleanCourseName(o.optString("name", ""))
            if (!validCourseName(name)) continue
            raw += Course(
                id = 0,
                name = name,
                teacher = cleanAuxText(o.optString("teacher", "")),
                location = cleanAuxText(o.optString("location", "")),
                dayOfWeek = day,
                startPeriod = sp.coerceIn(1, 16),
                endPeriod = ep.coerceIn(1, 16),
                colorIndex = 0
            )
        }
        if (raw.isEmpty()) return Report(emptyList(), arr.length(), "云端返回的课程都没能解析成有效条目")
        val merged = mergeSpans(raw).mapIndexed { i, c ->
            c.copy(
                id = 1000L + i + (System.currentTimeMillis() % 1000) * 1000L,
                colorIndex = i % Course.barColors.size
            )
        }
        return Report(merged, arr.length(), trImport("云端识别 ${merged.size} 门课", "Cloud OCR: ${merged.size} courses"))
    }

    /** 从模型回复里取出 JSON 数组：容忍 ```json 围栏、前后多余说明文字 */
    private fun extractJsonArray(text: String): org.json.JSONArray? {
        val t = text.trim()
        val fenced = Regex("```(?:json|JSON)?\\s*([\\s\\S]*?)```").find(t)
        val body = fenced?.groupValues?.get(1)?.trim() ?: t
        val start = body.indexOf('[')
        val end = body.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return runCatching { org.json.JSONArray(body.substring(start, end + 1)) }.getOrNull()
    }

    /** 星期字段：支持 1-7、周一/星期一/礼拜一、Mon..Sun */
    internal fun parseDay(raw: String): Int? {
        val s = raw.trim()
        s.toIntOrNull()?.let { if (it in 1..7) return it }
        val cn = Regex("星期|礼拜|周").find(s)
        if (cn != null) {
            val ch = s.firstOrNull { DAY_OF.containsKey(it) }
            ch?.let { return DAY_OF[it] }
        }
        val en = s.take(3).lowercase().takeIf { it.isNotEmpty() }
        return when (en) {
            "mon" -> 1; "tue" -> 2; "wed" -> 3; "thu" -> 4
            "fri" -> 5; "sat" -> 6; "sun" -> 7
            else -> null
        }
    }

    /** 节次宽松解析：纯数字 / 「第2节」/ 中文数字都能认；认不出返回 null */
    internal fun periodLoose(raw: String): Int? {
        val s = raw.trim()
        s.toIntOrNull()?.let { return it }
        parsePeriod(s)?.let { return it }
        Regex("\\d{1,2}").find(s)?.value?.toIntOrNull()?.let { return it }
        return null
    }

    /** 课名是否有效：去空白后至少 2 个字，且不是时间 / 日期 / 周次 / 纯数字 */
    private fun validCourseName(name: String): Boolean {
        if (name.replace(" ", "").length < 2) return false
        if (isNonCourseText(name.replace(" ", ""))) return false
        if (name.any { it.isDigit() } && name.length <= 2) return false
        return true
    }

    /** 老师 / 地点等辅助字段：只剥掉多余符号，保留原文 */
    private fun cleanAuxText(raw: String): String =
        cleanCourseName(raw).take(24)

    private fun trImport(zh: String, en: String): String =
        com.example.my.ui.i18n.tr(zh, en)

    /** 纯几何解析：输入 OCR 文本框，输出课程网格。抽出来便于单测，不依赖图片 / 模型 */
    internal fun parseLines(rawLines: List<Line>): Report {
        // OCR 输出先做一次纯文本归一：全角数字/字母、全角空格、连字符变体。
        // 只改字符形态，不改几何坐标，因此原来的网格还原逻辑完全不需要动。
        val all = rawLines.map { it.copy(text = normalizeText(it.text)) }
            .filter { it.text.isNotBlank() }
        val headers = all.mapNotNull { line ->
            val compact = line.text.replace(" ", "")
            val m = HEADER_RE.find(compact) ?: return@mapNotNull null
            val day = DAY_OF[m.groupValues[1].firstOrNull()] ?: return@mapNotNull null
            day to line
        }
        val periodLabels = all.mapNotNull { line ->
            val compact = line.text.replace(" ", "")
            val m = PERIOD_RE.matchEntire(compact) ?: return@mapNotNull null
            val p = parsePeriod(m.groupValues[1]) ?: return@mapNotNull null
            if (p !in 1..12) null else p to line
        }

        // 表头下方才是课程区：状态栏时间、「2026年11月9日 起」这类文字都在表头之上，直接剔除
        val headerBottom = headers.maxOfOrNull { it.second.rect.bottom } ?: Int.MIN_VALUE

        val content = all.filter { line ->
            val compact = line.text.replace(" ", "")
            HEADER_RE.matches(compact).not() &&
                PERIOD_RE.matchEntire(compact) == null &&
                !isNonCourseText(compact) &&
                NOISE.none { compact.contains(it) } &&
                line.rect.centerY() > headerBottom
        }
        if (content.isEmpty()) {
            return Report(emptyList(), all.size, "只看到表头和时间，没识别到课程内容")
        }

        // ---------- 列：星期 ----------
        val contentLeft = content.minOf { it.rect.left }
        val contentRight = content.maxOf { it.rect.right }
        val labelRight = periodLabels.maxOfOrNull { it.second.rect.right } ?: contentLeft

        val columns: List<Pair<Int, IntRange>> = run {
            val hs = headers.sortedBy { it.second.rect.centerX() }
            if (hs.size >= 3) {
                val centers = hs.map { it.first to it.second.rect.centerX() }
                centers.mapIndexed { i, (day, x) ->
                    val left = if (i == 0) {
                        val gap = (centers.getOrNull(1)?.second ?: x) - x
                        x - (gap / 2).coerceAtLeast(1)
                    } else {
                        (centers[i - 1].second + x) / 2
                    }
                    val right = if (i == centers.lastIndex) {
                        val gap = x - (centers.getOrNull(i - 1)?.second ?: x)
                        x + (gap / 2).coerceAtLeast(1)
                    } else {
                        (x + centers[i + 1].second) / 2
                    }
                    day to (left..right)
                }
            } else {
                // 没有表头：从时间列右侧开始把内容区均分成 7 列
                val x0 = (labelRight + 4)
                val x1 = maxOf(contentRight, x0 + 7)
                val step = (x1 - x0) / 7.0
                (1..7).map { i ->
                    val l = (x0 + step * (i - 1)).toInt()
                    val r = (x0 + step * i).toInt()
                    i to (l..r)
                }
            }
        }

        // ---------- 行：节次 ----------
        val orderedLabels = periodLabels.sortedBy { it.second.rect.top }
        val rowBands: List<Pair<Int, IntRange>> = run {
            if (orderedLabels.size >= 2) {
                orderedLabels.mapIndexed { i, (p, line) ->
                    val y = line.rect.centerY()
                    val top = if (i == 0) {
                        val gap = (orderedLabels.getOrNull(1)?.second?.rect?.centerY() ?: y) - y
                        y - (gap / 2).coerceAtLeast(1)
                    } else {
                        (orderedLabels[i - 1].second.rect.centerY() + y) / 2
                    }
                    val bottom = if (i == orderedLabels.lastIndex) {
                        val gap = y - (orderedLabels.getOrNull(i - 1)?.second?.rect?.centerY() ?: y)
                        y + (gap / 2).coerceAtLeast(1)
                    } else {
                        (y + orderedLabels[i + 1].second.rect.centerY()) / 2
                    }
                    p to (top..bottom)
                }
            } else {
                // 没有节次行号：按 y 间距聚类，天然顺序即第 1、2、3 节
                val sorted = content.sortedBy { it.rect.centerY() }
                val typicalH = sorted.map { it.rect.height() }.sorted().getOrElse(sorted.size / 2) { 20 }
                val gapThreshold = (typicalH * 2).coerceAtLeast(24)
                val clusters = mutableListOf<MutableList<Line>>()
                sorted.forEach { line ->
                    val last = clusters.lastOrNull()
                    val lastY = last?.maxOf { it.rect.centerY() } ?: Int.MIN_VALUE
                    if (last != null && line.rect.centerY() - lastY <= gapThreshold) last += line
                    else clusters += mutableListOf(line)
                }
                clusters.mapIndexed { i, cluster ->
                    val top = cluster.minOf { it.rect.top }
                    val bottom = cluster.maxOf { it.rect.bottom }
                    (i + 1) to (top..bottom)
                }
            }
        }

        // ---------- 落格子 ----------
        // 先按列分组、列内按纵向间距聚类成「一门课的整块文字」，再整块落到最近的节次。
        // 逐行直接判行会把压在分界线上的地点行甩到下一节，所以这里以整块的重心定位。
        fun dayOfLine(line: Line): Int? {
            val cx = line.rect.centerX()
            return columns.firstOrNull { cx in it.second }?.first
                ?: columns.minByOrNull { kotlin.math.abs((it.second.first + it.second.last) / 2 - cx) }?.first
        }

        fun periodOf(y: Int): Int? = rowBands.minByOrNull {
            kotlin.math.abs((it.second.first + it.second.last) / 2 - y)
        }?.first

        val cells = LinkedHashMap<Pair<Int, Int>, MutableList<Line>>()
        content.groupBy { dayOfLine(it) ?: 0 }.forEach { (day, columnLines) ->
            if (day == 0) return@forEach
            val sorted = columnLines.sortedBy { it.rect.centerY() }
            val typicalH = sorted.map { it.rect.height() }.sorted().getOrElse(sorted.size / 2) { 20 }
            val gapLimit = (typicalH * 1.6).toInt().coerceAtLeast(18)

            var block = mutableListOf<Line>()
            var lastBottom = Int.MIN_VALUE
            fun flushBlock() {
                if (block.isEmpty()) return
                val cy = block.sumOf { it.rect.centerY() } / block.size
                val period = periodOf(cy) ?: return
                cells.getOrPut(day to period) { mutableListOf() } += block
                block = mutableListOf()
            }
            sorted.forEach { line ->
                if (lastBottom != Int.MIN_VALUE && line.rect.top - lastBottom > gapLimit) flushBlock()
                block += line
                lastBottom = maxOf(lastBottom, line.rect.bottom)
            }
            flushBlock()
        }

        val parsed = cells.mapNotNull { (key, lines) -> parseCell(key.first, key.second, lines) }
            .sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }, { it.name }))

        val merged = mergeSpans(parsed).mapIndexed { i, c ->
            c.copy(
                id = 1000L + i + (System.currentTimeMillis() % 1000) * 1000L,
                colorIndex = i % Course.barColors.size
            )
        }

        val note = buildString {
            append("识别 ").append(all.size).append(" 行文字，")
            append("列").append(columns.size).append(" · 行").append(rowBands.size).append("，")
            append("得到 ").append(merged.size).append(" 门课")
            if (headers.size < 3) append("（未找到星期表头，按 7 列均分）")
            if (orderedLabels.size < 2) append("（未找到节次行号，按内容行聚类）")
        }
        return Report(merged, all.size, note)
    }

    /**
     * OCR 文本归一化：
     *  - 全角 ASCII（１２３、ＡＢＣ、（））→ 半角；
     *  - 全角空格、零宽字符 → 普通空格；
     *  - 各种连字符/破折号 → '-'，避免「教１－２０１」这类地点被切成两段；
     *  - 连续空白折叠。
     * 只处理字符，不猜测语义，识别结果错误仍由用户在预览里改。
     */
    internal fun normalizeText(raw: String): String {
        val mapped = StringBuilder(raw.length)
        raw.forEach { ch ->
            when {
                ch.code in 0xFF01..0xFF5E -> mapped.append((ch.code - 0xFEE0).toChar())
                ch == '\u3000' || ch == '\u00A0' -> mapped.append(' ')
                ch == '\u200B' || ch == '\uFEFF' -> Unit
                ch == '－' || ch == '—' || ch == '–' || ch == '−' -> mapped.append('-')
                ch == '―' -> mapped.append('-')
                else -> mapped.append(ch)
            }
        }
        return mapped.toString()
            .replace(Regex("[\\t\\u000B\\f\\r]+"), " ")
            .replace(Regex("  +"), " ")
            .trim()
    }

    /** 「1」「第1节」「第一节」「第十一节」→ 1..12；认不出返回 null */
    internal fun parsePeriod(raw: String): Int? {
        val s = raw.trim()
        s.toIntOrNull()?.let { return it }
        if (s.startsWith("十")) {
            if (s.length == 1) return 10
            return CN_NUM[s[1]]?.let { 10 + it }
        }
        if (s.length == 1) return CN_NUM[s[0]]
        return null
    }

    /** 去掉「（1-16周）」「[单周]」这类注记；再剥 markdown 标题、序号前缀和首尾引号符号 */
    internal fun cleanCourseName(raw: String): String {
        var s = raw.replace('\u3000', ' ').trim()
        s = NAME_NOTE.replace(s, "")
        // markdown 标题前缀：### / ## / #
        s = Regex("^#{1,6}\\s*").replace(s, "")
        // 行首序号：一、 二、 1. 1、 (一) ① 等
        s = Regex("^[（(]?[0-9一二三四五六七八九十]{1,3}[)）.、．]\\s*").replace(s, "")
        s = Regex("^[①②③④⑤⑥⑦⑧⑨⑩⑪⑫]\\s*").replace(s, "")
        // 首尾残留的引号 / 括号 / 项目符号
        s = s.trim().trim('"', '“', '”', '‘', '’', '「', '」', '『', '』', '\'',
            '`', '*', '-', '·', '•', '#', '｜', '|', ' ', '\t')
        return Regex("\\s+").replace(s, " ").trim()
    }

    /** 一个格子里的文字 → 一门课 */
    private fun parseCell(day: Int, period: Int, lines: List<Line>): Course? {
        val segs = lines.sortedWith(compareBy({ it.rect.top }, { it.rect.left }))
            .map { it.text.trim().trimStart('▲', '△', '●', '*', '-', '·') }
            .filter { it.isNotBlank() }
        if (segs.isEmpty()) return null

        val name = cleanCourseName(segs.first()).take(24)
        if (name.replace(" ", "").length < 2) return null
        if (name.any { it.isDigit() } && name.length <= 2) return null
        // 时间 / 日期 / 周次 / 纯数字是左侧时间栏与表头带进来的，不是课程
        if (isNonCourseText(name.replace(" ", ""))) return null

        val rest = segs.drop(1)
        var teacher = ""
        var location = ""
        rest.forEach { s ->
            when {
                location.isBlank() && LOCATION_HINTS.any { s.contains(it) } -> location = s.take(24)
                location.isBlank() && LOCATION_PATTERN.containsMatchIn(s) && s.length <= 18 -> location = s.take(24)
                teacher.isBlank() && s.length <= 8 && s.none { it.isDigit() } &&
                    LOCATION_HINTS.none { s.contains(it) } -> teacher = s
            }
        }
        return Course(
            id = 0,
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = day.coerceIn(1, 7),
            startPeriod = period.coerceIn(1, 12),
            endPeriod = period.coerceIn(1, 12),
            colorIndex = 0
        )
    }

    /** 同一列里连续节次的同名课合并成一门（如「高等数学」第 1-2 节） */
    private fun mergeSpans(list: List<Course>): List<Course> {
        val out = mutableListOf<Course>()
        list.groupBy { it.dayOfWeek to it.name.replace(" ", "") }.forEach { (_, group) ->
            val sorted = group.sortedBy { it.startPeriod }
            var cur: Course? = null
            sorted.forEach { c ->
                val prev = cur
                if (prev != null && c.startPeriod <= prev.endPeriod + 1) {
                    cur = prev.copy(endPeriod = maxOf(prev.endPeriod, c.endPeriod))
                } else {
                    prev?.let { out += it }
                    cur = c
                }
            }
            cur?.let { out += it }
        }
        return out.sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }))
    }

    private val LOCATION_HINTS = listOf("楼", "室", "馆", "场", "机房", "实验", "中心", "校区", "阶梯", "报告厅")
    private val LOCATION_PATTERN = Regex("[A-Za-z]?\\d{3,4}|[A-Za-z]-?\\d{2,3}")
}
