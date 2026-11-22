package com.example.my.model

import androidx.compose.ui.graphics.vector.ImageVector

/** 单个景点/行程点 */
data class Spot(
    val name: String,
    val note: String,
    val cost: String,
    val tips: String? = null,
    val special: Boolean = false,
    val specialReason: String? = null,
    val seed: Int = 0
)

/** 一天的行程 */
data class DayPlan(
    val day: Int,
    val title: String,
    val spots: List<Spot>,
    val special: Boolean = false,
    val specialReason: String? = null
)

/** 评论回复（楼中楼） */
data class CommentReply(
    val user: String,
    val time: String,
    val content: String,
    val likes: Int = 0,
    val liked: Boolean = false,
    val dislikes: Int = 0,
    val disliked: Boolean = false,
    val replyTo: String? = null,
    val id: Int = 0
)

/** 攻略评论 */
data class Comment(
    val user: String,
    val time: String,
    val content: String,
    val likes: Int = 0,
    val liked: Boolean = false,
    val dislikes: Int = 0,
    val disliked: Boolean = false,
    val replies: List<CommentReply> = emptyList(),
    val id: Int = 0
)

/** 花费条目 */
data class CostItem(
    val label: String,
    val amount: String,
    val total: Boolean = false
)

/** 攻略 */
data class Guide(
    val id: Int,
    val title: String,
    val city: String,
    val days: Int,
    val type: String,
    val author: String,
    val authorIntro: String,
    val likes: Int,
    val favs: Int,
    val cost: String,
    val coverSeed: Int,
    val tags: List<String>,
    val intro: String,
    val costBreakdown: List<CostItem>,
    val tips: List<String>,
    val itinerary: List<DayPlan>,
    val commentList: List<Comment>,
    val featured: Boolean = false,
    val theme: String = "",
    /** 地区大区分类（西南/华东/华北/西北/东北/华南/华中），用于发现页按地区筛选 */
    val region: String = "",
    val viewedCount: Int = 0,
    val bannerTitle: String = "",
    val bannerSubtitle: String = "",
    val liked: Boolean = false,
    val favored: Boolean = false,
    val coverResId: Int? = null,
    /** 攻略图集（多张大图，用于顶部轮播与全屏查看；空则回退 coverResId 单图） */
    val images: List<Int> = emptyList(),
    /** 每张图的来源标注（与 images 一一对应，空字符串表示无标注/官方图） */
    val imageSources: List<String> = emptyList(),
    /** 作者用户 id；-1 表示非当前用户（mock/他人），用于"我的攻略"过滤 */
    val authorId: Int = -1
)

/** 旅行路线（回忆） */
data class Route(
    val id: Int,
    val title: String,
    val year: Int,
    val date: String,
    val days: Int,
    val cities: List<String>,
    val coverSeed: Int,
    val special: Boolean = false,
    val specialName: String? = null,
    val specialReason: String? = null,
    val note: String = "",
    val tags: List<String>,
    val totalCost: String,
    val isPublic: Boolean,
    val photoCount: Int,
    val itinerary: List<DayPlan>,
    val coverResId: Int? = null
)

/** 足迹地图上的标记点（坐标为地图数据坐标系 0-774/0-569） */
data class Footprint(
    val id: Int,
    val city: String,
    val name: String,
    val note: String,
    val date: String,
    val x: Float,
    val y: Float,
    val special: Boolean = false,
    val specialReason: String? = null,
    val seed: Int = 0,
    val routeId: Int? = null,
    /**
     * 照片在应用私有目录下的绝对路径（filesDir/footprints/{id}/xxx.webp 或旧版 .jpg）。
     * 之所以不用 content:// Uri：PhotoPicker 返回的 Uri 长期有效性不可靠（来源应用卸载、
     * 云盘文件下线都会失效），且导出长图时需要同步解码，本地文件才有确定性。
     * 旧数据无此字段时为空列表，由 Persistence 反序列化时兜底。
     */
    val photoPaths: List<String> = emptyList(),
    /**
     * 出行日期的精确时间戳（EXIF 回填或用户手选）。
     * 有了它就不必再解析 date 字符串——历史数据里 "yyyy年M月" 和 "yyyy-MM-dd"
     * 两种格式混用，靠正则推断年月既脆弱又要维护三套解析逻辑。
     * null 表示旧数据/未知，此时才回退到解析 date 字符串。
     */
    val dateMillis: Long? = null,
    /**
     * 真实经纬度（GCJ-02）。
     *
     * x/y 是「中国示意地图」上的投影坐标，由经纬度线性映射而来；两者互为逆运算但
     * 会损失精度，也无法反向用于查天气 / 算距离 / 在高德底图上重新定位。
     * 有了 lat/lon，选点页可以原样回到用户上次选的那一点，而不是「反算出来的大概位置」。
     * 旧数据只有 x/y，此处为 null，调用方需自行降级。
     */
    val lat: Double? = null,
    val lon: Double? = null,
    /**
     * 路线型足迹：自动记录结束后保存的完整轨迹（按时间顺序，GCJ-02）。
     * 普通足迹为空列表。
     */
    val route: List<RoutePoint> = emptyList(),
    /**
     * 途中用户手动标记的位置（可选，带一张照片）。
     * 记录过程中点「标记」时产生，保存后展示在足迹详情里。
     */
    val marks: List<RouteMark> = emptyList(),
    /** 路线总距离（公里）；0 表示不是路线记录 */
    val distanceKm: Double = 0.0,
    /** 路线总时长（秒）；0 表示不是路线记录 */
    val durationSec: Long = 0L,
    /**
     * 视频 / 实况的本地路径（应用私有目录下的 mp4）。
     * 动态照片（Motion Photo）会把内嵌的那段视频抠出来存成独立 mp4，并置 [videoIsLive]。
     */
    val videoPath: String? = null,
    /** 多视频足迹：所有本地视频路径；[videoPath] 作为旧字段保留第一条，旧数据自动兼容 */
    val videoPaths: List<String> = emptyList(),
    /** 这段视频是否来自「实况/动态照片」的内嵌片段 */
    val videoIsLive: Boolean = false,
    /** 压缩后的实际体积（字节）；0 表示未知 */
    val videoBytes: Long = 0L,
    /** 配乐本地缓存路径（选好的 BGM 会下载到足迹目录，离线也能放） */
    val bgmPath: String? = null,
    val bgmTitle: String? = null,
    val bgmArtist: String? = null,
    /** 配乐来源，例如 "Tunetank" */
    val bgmSource: String? = null,
    /**
     * 起点照片 / 终点照片：用户在详情里单独指定的那一张（私有目录绝对路径）。
     *
     * 为什么不从 [photoPaths] 里「按顺序猜第一张是起点」：照片顺序由选择顺序决定，
     * 与轨迹方向毫无关系，猜出来的起点照片经常是中途随手拍的那张。单独存一个字段，
     * 用户点哪张就用哪张，也不再和照片轮播互相干扰。
     */
    val startPhotoPath: String? = null,
    val endPhotoPath: String? = null,
    /** 途中照片：没有对应标记点、但用户想算作「路上」的那些 */
    val midPhotoPaths: List<String> = emptyList()
) {
    /** 所有视频（兼容旧数据只有 videoPath 的情况） */
    val allVideoPaths: List<String> get() = videoPaths.ifEmpty { listOfNotNull(videoPath) }
}

/** 路线足迹里的一个轨迹点（时间是采样时刻，用于算时长与还原速度） */
data class RoutePoint(val lat: Double, val lon: Double, val time: Long)

/**
 * 路线足迹途中的手动标记点。
 *
 * [photoPath] 是应用私有目录下的绝对路径（可空）：用户可以在标记时顺手拍/选一张，
 * 也可以只标个位置。
 */
data class RouteMark(
    val lat: Double,
    val lon: Double,
    val note: String,
    val photoPath: String? = null,
    val time: Long = 0L
)

/**
 * 记录过程中尚未落盘的途中标记。
 *
 * 此时照片还是 content:// Uri（用户刚选的，还没复制进私有目录），
 * 结束保存时才复制成 m1.jpg、m2.jpg 并转成 [RouteMark]。
 */
data class PendingRouteMark(
    val lat: Double,
    val lon: Double,
    val note: String,
    val photoUri: android.net.Uri? = null,
    val time: Long = 0L
)

/** 出行计划（新建计划后本地持久化，出发前 1 天系统通知提醒） */
data class TripPlan(
    val id: Long,
    val name: String,
    val destination: String,
    /** 出发日期 yyyy-MM-dd */
    val startDate: String,
    val days: Int,
    val note: String,
    val createdAt: Long,
    /** 已归档：不再出现在主列表，可随时恢复 */
    val archived: Boolean = false,
    /** 每日安排，如「D1｜抵达大理 · 古城闲逛」 */
    val dailyPlan: List<String> = emptyList(),
    /** 已勾选打包清单项（与基础模板 + 自定义项合并展示） */
    val packedItems: List<String> = emptyList(),
    /** 用户自行添加的打包物品，统一按「分类|物品」编码 */
    val customPacking: List<String> = emptyList(),
    /** 已完成超过 30 天自动归档（用户可关闭） */
    val autoArchive: Boolean = true,
    /** 自定义排序序号；-1 表示未参与自定义排序 */
    val manualOrder: Int = -1,
    /** 同行人（姓名/昵称） */
    val companions: List<String> = emptyList(),
    /** 是否开启出发提醒 */
    val reminderEnabled: Boolean = true,
    /** 提前天数（0 = 出发当天） */
    val reminderOffsetDays: Int = 1,
    /** 提醒时刻（24 小时制） */
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    /** 提醒方式：震动 / 铃声 */
    val reminderVibrate: Boolean = true,
    val reminderSound: Boolean = true
)

/**
 * 一节课（课表系统）。dayOfWeek: 1=周一 .. 7=周日；startPeriod/endPeriod: 第几节，从 1 开始。
 *
 * 周次：大学课表普遍有「单双周」「第 1-8 周」这种排法，所以课程不再默认每周都出现。
 * [weekMode] 0=每周 1=单周 2=双周 3=指定周范围；[weekStart]/[weekEnd] 仅模式 3 生效。
 * 旧数据没有这三个字段，反序列化时取默认值 = 每周，升级不丢课。
 */
data class Course(
    val id: Long,
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int = startPeriod,
    val colorIndex: Int = 0,
    val weekMode: Int = WEEK_EVERY,
    val weekStart: Int = 1,
    val weekEnd: Int = 20
) {
    val safeWeekMode: Int get() = weekMode.coerceIn(0, 3)

    /** 从第几周到第几周（模式 3 用；填反了自动纠正） */
    val weekFrom: Int get() = minOf(weekStart, weekEnd).coerceAtLeast(1)
    val weekTo: Int get() = maxOf(weekStart, weekEnd).coerceAtLeast(1)

    /** 这一周有没有这节课 */
    fun isInWeek(week: Int): Boolean {
        val w = week.coerceAtLeast(1)
        return when (safeWeekMode) {
            WEEK_ODD -> w % 2 == 1
            WEEK_EVEN -> w % 2 == 0
            WEEK_RANGE -> w in weekFrom..weekTo
            else -> true
        }
    }

    /** 与另一节课在节次上是否重叠 */
    fun overlapsPeriod(other: Course): Boolean =
        dayOfWeek == other.dayOfWeek &&
            startPeriod.coerceAtMost(endPeriod) <= other.endPeriod.coerceAtLeast(other.startPeriod) &&
            other.startPeriod.coerceAtMost(other.endPeriod) <= endPeriod.coerceAtLeast(startPeriod)

    companion object {
        const val WEEK_EVERY = 0
        const val WEEK_ODD = 1
        const val WEEK_EVEN = 2
        const val WEEK_RANGE = 3

        /** 两门课是否存在「同一周同时出现」的可能，用于编辑时的冲突提示 */
        fun weeksOverlap(a: Course, b: Course): Boolean =
            (1..MAX_WEEKS).any { a.isInWeek(it) && b.isInWeek(it) }

        /** 冲突判定/周次快选的扫描上界，也用于设置里 totalWeeks 的上限 */
        const val MAX_WEEKS = 60

        /** 默认节次时间表（与桌面小组件设计稿一致） */
        val periods = listOf(
            "08:30–10:00",
            "10:15–11:45",
            "14:00–15:30",
            "16:00–17:30",
            "19:00–20:30"
        )
        val periodStart = listOf("08:30", "10:15", "14:00", "16:00", "19:00")
        val periodEnd = listOf("10:00", "11:45", "15:30", "17:30", "20:30")
        val weekDays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val weekDaysEn = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        /** 左侧序号条配色（与设计稿一致：青/紫/蓝/紫循环） */
        val barColors = listOf(0xFF2DD4BF.toInt(), 0xFFA78BFA.toInt(), 0xFF60A5FA.toInt(), 0xFFC084FC.toInt())
    }
}

/**
 * 内置出行清单：分类模板 + 「分类|物品」编码。
 * 用编码而不是纯物品名，避免「充电器」在电子类和证件类重名时勾选状态互相串。
 * 旧版本自定义物品（无「|」）在解析时归入「其他」，保证升级不丢数据。
 */
object Packing {
    val categories = listOf("证件类", "衣物类", "电子类", "药品类", "其他")

    val template: Map<String, List<String>> = linkedMapOf(
        "证件类" to listOf("身份证", "护照 / 签证", "机票 / 火车票", "酒店预订信息", "现金 / 银行卡"),
        "衣物类" to listOf("换洗衣物", "外套", "睡衣", "泳衣", "舒适鞋"),
        "电子类" to listOf("手机", "充电器", "充电宝", "耳机", "数据线"),
        "药品类" to listOf("常用药", "创可贴", "晕车药", "防晒霜", "驱蚊液"),
        "其他" to listOf("洗漱用品", "雨具", "水杯", "零食")
    )

    fun key(category: String, item: String): String = "$category|$item"

    fun parse(key: String): Pair<String, String> =
        if ('|' in key) key.substringBefore('|') to key.substringAfter('|')
        else "其他" to key

    fun label(key: String): String = parse(key).second

    fun category(key: String): String = parse(key).first

    /** 模板 + 自定义，按分类分组；返回「分类 -> 物品 key 列表」 */
    fun grouped(customPacking: List<String>): List<Pair<String, List<String>>> {
        val customByCategory = customPacking.groupBy { category(it) }
        return categories.map { cat ->
            val builtin = template[cat].orEmpty().map { key(cat, it) }
            val custom = customByCategory[cat].orEmpty()
            cat to (builtin + custom).distinct()
        }
    }

    fun allKeys(customPacking: List<String>): List<String> = grouped(customPacking).flatMap { it.second }
}

/** 旅行勋章 */
data class Medal(
    val name: String,
    val desc: String,
    val icon: ImageVector,
    val unlocked: Boolean,
    val current: Int = 0,
    val target: Int = 1
)

/** 用户信息 */
data class UserData(
    val nickname: String,
    val signature: String,
    val avatarText: String,
    val citiesCount: Int,
    val footprintCount: Int,
    val daysCount: Int,
    val followers: Int,
    val following: Int,
    val likesReceived: Int,
    val medals: List<Medal>,
    val avatarUrl: String? = null,
    /** 注册日期（yyyy-MM-dd），null 表示未知（新用户/未同步） */
    val joinedAt: String? = null
)

/** 用户设置（DataStore 持久化） */
data class UserSettings(
    val nickname: String = "林屿",
    val signature: String = "山高路远，看世界，也找自己",
    val defaultPublic: Boolean = true,
    val notifEnabled: Boolean = true,
    val shareEnabled: Boolean = true,
    /** 液态玻璃效果：默认开，只在导航栏/卡片等关键元素上生效；关闭后走半透明降级 */
    val liquidGlass: Boolean = true
)

/** 通知类型 */
enum class NotifType { LIKE, COMMENT, FOLLOW, SYSTEM }

/** 通知消息 */
data class AppNotification(
    val id: Int,
    val type: NotifType,
    val title: String,
    val body: String,
    val time: String,
    val read: Boolean = false
)
