package com.example.my.data.music

/**
 * 歌名识别：把杂乱的文件名 / MediaStore 标题还原成「歌名 + 歌手」。
 *
 * 真实音乐文件常见的脏数据：
 *   1231红尘客栈            前导序号紧贴中文（没有空格，旧的 ^\d{1,3}[.\-_ ]+ 去不掉）
 *   01.周杰伦 - 红尘客栈    音轨号 + 歌手 + 歌名（歌手在前）
 *   红尘客栈 - 周杰伦.mp3   歌名在前！网页下载 / 曲库导出的文件大量是这种
 *   02 - Shape of You      英文音轨号（但要保住 "500 Miles" 这类真歌名）
 *   【官方MV】红尘客栈(Live) 括号标注 / 音质标记 / 版本后缀
 *
 * 设计原则：宁可少剥，不可误伤。纯数字歌名、"500 Miles" 这类以数字开头的
 * 合法歌名必须原样保留；只有「数字后面紧跟中文」或「数字后面是明显分隔符」
 * 才判定为音轨号。清洗结果同时用于列表显示与歌词检索的查询词。
 *
 * 「谁在前」不能写死：MediaStore 的 ARTIST 是权威，文件名里左右两段都可能
 * 是歌手。parse() 会用 ARTIST / 常见歌手词典判定方向，避免把「红尘客栈 - 周杰伦」
 * 显示成大字「周杰伦」、小字「红尘客栈」。repair() 用于自愈已经入库的历史反数据。
 *
 * 与 build/song_name_parser_test.py 是同一套算法（那边是 Python 镜像 + 用例）。
 */
data class SongNameInfo(
    /** 识别后的歌名 */
    val title: String,
    /** 从 "歌手 - 歌名" / "歌名 - 歌手" 里拆出来的歌手；没有则为 null */
    val artist: String?,
    /** 去掉扩展名后的原始名字 */
    val raw: String,
    /** 是否真的做过清洗，UI 可据此提示「已识别为 …」 */
    val changed: Boolean
)

object SongNameParser {

    private val EXTENSION = Regex(
        """\.(mp3|m4a|aac|flac|wav|ogg|wma|ape|opus|mp4|m4b|3gp)$""",
        RegexOption.IGNORE_CASE
    )
    private val LEADING_INDEX = Regex("""^(\d{1,4})([\s\S]*)$""")
    private val DISC_PREFIX = Regex("""(?i)^(disc|cd|track|曲目|第)\s*\d{1,2}\s*[\s.\-_、,，:：]+""")
    private val SEPARATORS = setOf('.', '-', '_', '、', ',', '，', ':', '：', '–', '—', '－')
    private val BRACKET_TAG = Regex("""[（(\[【][^）)\]】]{0,24}[）)\]】]""")
    private val TAIL_TAG = Regex(
        """(?i)[\s\-_·]*(live|remix|cover|伴奏|纯音乐|翻唱|demo|inst|karaoke|官方版|mv|hd|hq|sq|无损|高清|超清|320k|128k|flac|ape|wav|mp3)(版|音质|音源)?\s*$"""
    )
    private val FEAT_TAIL = Regex("""(?i)\s+(feat\.?|ft\.?|featuring)\s+.*$""")
    private val EDGE_JUNK = Regex("""^[\s\-_·.,，、:：]+|[\s\-_·.,，、:：]+$""")
    private val ARTIST_SEPARATORS = listOf(" - ", " – ", " — ", " -", "- ", "_", "-")
    private val ARTIST_TOKEN_SPLIT = Regex("""[\s.·•,，/|&]+""")
    private val NON_NAME = Regex("""[^\p{L}\p{N}]""")

    /**
     * 常见歌手（已归一化：小写、去空格与符号）。只用于「方向判定」——
     * 命中不代表一定是歌手，还要看另一段是否更像歌名；不命中就维持原约定，
     * 所以冷门歌手不会误伤。用户可在列表长按里手动「互换歌名和歌手」兜底。
     */
    private val KNOWN_ARTIST_KEYS = setOf(
        "周杰伦", "林俊杰", "陈奕迅", "薛之谦", "许嵩", "孙燕姿", "王菲", "邓紫棋",
        "五月天", "张杰", "张韶涵", "梁静茹", "刘德华", "张学友", "郭富城", "黎明",
        "周华健", "李荣浩", "毛不易", "华晨宇", "鹿晗", "王源", "王俊凯", "易烊千玺",
        "马嘉祺", "时代少年团", "汪苏泷", "徐良", "程响", "张碧晨", "周深", "单依纯",
        "陈粒", "赵雷", "朴树", "张国荣", "梅艳芳", "邓丽君", "费玉清", "蔡琴",
        "凤凰传奇", "苏打绿", "伍佰", "任贤齐", "周传雄", "阿杜", "林志炫", "张信哲",
        "张惠妹", "蔡依林", "萧敬腾", "杨宗纬", "李健", "刀郎", "庄心妍", "张艺兴",
        "李宇春", "张靓颖", "韩红", "孙楠", "那英", "田震", "刘欢", "腾格尔",
        "谭维维", "郁可唯", "黄丽玲", "容祖儿", "杨千嬅", "古巨基", "李克勤",
        "谭咏麟", "林子祥", "许冠杰", "蔡徐坤", "宋冬野", "马頔", "房东的猫",
        "taylorswift", "edsheeran", "adele", "coldplay", "maroon5", "beatles",
        "beyonce", "rihanna", "justinbieber", "eminem", "jaychou", "jjlin",
        "easonchan", "g em", "gem"
    )

    private val UNKNOWN_ARTISTS = setOf(
        "<unknown>", "unknown", "未知艺术家", "未知歌手", "未知", "本地文件",
        "网页音频", "audio", "artist"
    )

    private fun isCjk(c: Char): Boolean = c.code in 0x4E00..0x9FFF ||
        c.code in 0x3400..0x4DBF || c.code in 0x3040..0x30FF || c.code in 0xAC00..0xD7AF

    /** 去掉文件扩展名，但绝不用 substringBeforeLast('.') —— 会砍掉 "Mr. Brightside" */
    private fun stripExtension(s: String): String = s.trim().replace(EXTENSION, "").trim()

    /** 归一化，用于歌手比对：Taylor Swift / taylor-swift → taylorswift */
    private fun nameKey(s: String): String =
        stripTags(stripExtension(s)).lowercase().replace(NON_NAME, "")

    private fun similar(a: String, b: String): Boolean {
        val x = nameKey(a)
        val y = nameKey(b)
        if (x.length < 2 || y.length < 2) return false
        return x == y || x.contains(y) || y.contains(x)
    }

    /** 这一段是否「像歌手」：命中常见歌手，或明显是乐团/组合名 */
    private fun looksLikeArtist(s: String): Boolean {
        val t = stripTags(stripExtension(s)).trim()
        if (t.isBlank() || t.length > 24) return false
        val k = nameKey(t)
        if (k.isBlank()) return false
        // 「G.E.M. 邓紫棋」「周杰伦 Jay」这类复合艺名归一后不会精确命中词典，
        // 再按空格/点号切成片段判断，避免把英文缩写 + 中文名的歌手漏掉。
        val tokens = t.split(ARTIST_TOKEN_SPLIT).map { nameKey(it) }
            .filter { it.length >= 2 }
        return k in KNOWN_ARTIST_KEYS ||
            tokens.any { it in KNOWN_ARTIST_KEYS } ||
            listOf("乐团", "乐队", "组合", "合唱团", "band", "duo")
                .any { t.contains(it, ignoreCase = true) }
    }

    /**
     * 剥掉前导音轨号。最多两轮，处理 "1. 02 红尘客栈" 这种双层编号。
     *  1231红尘客栈 → 红尘客栈（数字紧贴中文）
     *  01 红尘客栈  → 红尘客栈（空格 + 中文）
     *  02 - Shape of You → Shape of You（空格 + 分隔符）
     *  500 Miles / 1984  → 原样（数字是歌名的一部分）
     */
    private fun stripLeadingIndex(s: String): String {
        var cur = s.replace(DISC_PREFIX, "").trim()
        repeat(2) {
            val m = LEADING_INDEX.find(cur) ?: return@repeat
            val tail = m.groupValues[2]
            if (tail.isEmpty()) return@repeat
            val rest = tail.trimStart { it.isWhitespace() || it in SEPARATORS }
            if (rest.length < 2) return@repeat
            val head = tail.trimStart().firstOrNull()
            val noGap = !tail[0].isWhitespace() && tail[0] !in SEPARATORS
            val shouldStrip = when {
                noGap -> true                                  // 1231红尘客栈
                head != null && head in SEPARATORS -> true     // 01.Foo / 02 - Foo
                isCjk(rest[0]) -> true                          // 01 红尘客栈
                else -> false                                  // 500 Miles
            }
            if (!shouldStrip) return@repeat
            cur = rest
        }
        return cur
    }

    /** 从 "周杰伦 - 红尘客栈" / "红尘客栈 - 周杰伦" 里拆出左右两段 */
    private fun splitArtist(s: String): Pair<String?, String> {
        for (sep in ARTIST_SEPARATORS) {
            val idx = s.indexOf(sep)
            if (idx <= 0) continue
            val left = s.substring(0, idx).trim().replace(EDGE_JUNK, "")
            val right = s.substring(idx + sep.length).trim()
            if (left.isEmpty() || right.isEmpty()) continue
            if (left.length > 16 || left.count { it.isWhitespace() } > 3) continue
            if (left.all { it.isDigit() }) continue
            // "Shape_of_You" 不是歌手_歌名，下划线只在左侧像中文人名时才拆
            if (sep == "_" && !left.any { isCjk(it) }) continue
            // 无空格的纯 "-" 只认中文文件名（稻香-周杰伦），别把 "X-Ray" 拆成两半
            if (sep == "-" && !(left.any { isCjk(it) } && right.any { isCjk(it) })) continue
            return left to right
        }
        return null to s
    }

    /** 去掉版本 / 音质 / 现场版等标注，保留歌名主体 */
    private fun stripTags(s: String): String {
        var cur = s.replace(BRACKET_TAG, " ")
        cur = cur.replace(FEAT_TAIL, "")
        repeat(3) { cur = cur.replace(TAIL_TAG, "") }
        cur = cur.replace(Regex("""\s{2,}"""), " ").replace(EDGE_JUNK, "")
        return cur.trim()
    }

    /** 供歌词检索用：只做最小清洗，拿到干净的歌名 */
    fun clean(rawTitle: String): String {
        val raw = stripExtension(rawTitle)
        if (raw.isBlank()) return ""
        val parsed = parse(rawTitle)
        return parsed.title.ifBlank { raw }
    }

    /** 把「艺术家」字段归一：未知占位返回 null，顺带清洗音轨号 */
    fun cleanArtist(rawArtist: String?): String? {
        val s = stripTags(stripExtension(rawArtist.orEmpty())).trim()
        if (s.isBlank() || s.lowercase() in UNKNOWN_ARTISTS) return null
        return s.take(40)
    }

    /**
     * 完整识别。传入的 artist 可能是 MediaStore 的 "<unknown>"。
     *
     * 方向判定（修「歌名和歌手位置反了」）：
     *  1. MediaStore 的 ARTIST 是权威 —— 它和右段吻合，说明文件名是「歌名 - 歌手」；
     *  2. 没有权威 artist 时，右段命中常见歌手而左段不像 → 同样判定为「歌名 - 歌手」；
     *  3. 其余维持原约定「歌手 - 歌名」，保证 "周杰伦 - 红尘客栈" 不被改坏。
     */
    fun parse(rawTitle: String, rawArtist: String? = null): SongNameInfo {
        val raw = stripExtension(rawTitle)
        if (raw.isBlank()) return SongNameInfo("", cleanArtist(rawArtist), "", false)

        val noIndex = stripLeadingIndex(raw)
        val (embeddedArtist, afterSplit) = splitArtist(noIndex)
        val authoritative = cleanArtist(rawArtist)
        val left = stripTags(embeddedArtist.orEmpty())
        val right = stripTags(afterSplit)

        val swapped = embeddedArtist != null && left.isNotBlank() && right.isNotBlank() &&
            ((authoritative != null && similar(authoritative, right) &&
                !similar(authoritative, left)) ||
                (looksLikeArtist(right) && !looksLikeArtist(left)))

        val title = (if (swapped) left else right).ifBlank {
            stripTags(noIndex).ifBlank { raw }
        }
        val artist = authoritative ?: cleanArtist(if (swapped) right else left)
        return SongNameInfo(
            title = title,
            artist = artist,
            raw = raw,
            changed = title != raw
        )
    }

    /**
     * 自愈已经入库的历史反数据：歌名位置是常见歌手、歌手位置却不像歌手时交换。
     * 只做「高置信度」修正，冷门歌手交给用户长按里的「互换歌名和歌手」手动处理。
     */
    fun repair(title: String, artist: String): Pair<String, String>? {
        val t = stripTags(title).trim()
        val a = stripTags(artist).trim()
        if (t.isBlank() || a.isBlank()) return null
        if (a.lowercase() in UNKNOWN_ARTISTS) return null
        return if (looksLikeArtist(t) && !looksLikeArtist(a)) (a to t) else null
    }

    /** 检索用的「歌名 + 歌手」对，歌词源与手动搜索共用 */
    fun query(title: String, artist: String?): Pair<String, String> =
        clean(title) to (cleanArtist(artist).orEmpty())
}
