package com.example.my.data

import com.example.my.model.MicroAuthor
import com.example.my.model.MicroComment
import com.example.my.model.MicroPost

/**
 * 微足迹离线示例池。
 *
 * CloudConfig.ENABLED=false 时没有服务端，feed 用随包 assets 里的旅行照片构造
 * 「其他用户的公开足迹」。作者 id 一律为负，点开头像不会跳真实用户主页。
 * 图片路径统一 file:///android_asset 前缀，Coil 的 AssetUriFetcher 可直接加载。
 */
object MicroFeedSeed {

    private fun asset(name: String) = "file:///android_asset/photos/$name"

    val authors = listOf(
        MicroAuthor(-1, "阿茶", "茶", null, "在城市缝隙里找光 · 旅行摄影师", 2_800),
        MicroAuthor(-2, "向北", "北", null, "长期在路上，短期在人间", 1_260),
        MicroAuthor(-3, "麦子", "麦", null, "风光摄影 / 高原重度爱好者", 8_420, verified = true),
        MicroAuthor(-4, "梅里山脚", "梅", null, "守着雪山等日照金山的人", 3_150),
        MicroAuthor(-5, "一只鹿", "鹿", null, "慢慢走，慢慢拍", 926),
        MicroAuthor(-6, "小满", "满", null, "海边长大的人", 5_370),
        MicroAuthor(-7, "陈屿", "屿", null, "京都四季记录者", 1_830),
        MicroAuthor(-8, "山谷风", "风", null, "冰岛追光行程定制", 6_090, verified = true)
    )

    private fun comment(id: String, who: String, text: String, time: String, reply: String? = null) =
        MicroComment(id = id, authorName = who, text = text, time = time, replyTo = reply)

    /** 推荐流；关注/同城/我的/搜索都在 AppState 里按这套原始数据派生 */
    val posts: List<MicroPost> = listOf(
        MicroPost(
            id = "seed_meili",
            author = authors[3],
            place = "梅里雪山 · 日照金山",
            city = "迪庆",
            caption = "国家地理视角｜梅里神女峰，雪山与星河同框。\n国家地理镜头下的浪漫，在梅里女神峰找到了。夜幕低落，星光漫过山脊，等到第一缕金光落在雪顶，一切都值了。",
            tags = listOf("日照金山", "梅里雪山", "星空摄影"),
            imagePaths = listOf(asset("西藏.jpg"), asset("稻城亚丁.jpg")),
            bgmTitle = "原声 · 山谷风",
            baseLikes = 1715, baseComments = 206, baseShares = 495, favorites = 226,
            comments = listOf(
                comment("c1", "向北", "这个机位真的绝了，几点到的？", "2小时前"),
                comment("c2", "梅里山脚", "凌晨五点，云layer很关键", "1小时前", "向北")
            ),
            publishedAt = 1_763_400_000_000L
        ),
        MicroPost(
            id = "seed_chongqing",
            author = authors[0],
            place = "重庆 · 鹅岭二厂",
            city = "重庆",
            caption = "山城的夜是有层次的。\n从鹅岭二厂一路走到李子坝，轻轨从楼里穿过去，霓虹顺着坡往下淌。走累了就在梯坎边坐一会儿，风里全是火锅味。",
            tags = listOf("重庆", "山城夜景", "citywalk"),
            imagePaths = listOf(asset("重庆.jpg"), asset("长沙.jpg")),
            bgmTitle = "原声 · 阿茶",
            baseLikes = 892, baseComments = 74, baseShares = 131, favorites = 96,
            publishedAt = 1_763_316_000_000L
        ),
        MicroPost(
            id = "seed_xinjiang",
            author = authors[1],
            place = "新疆 · 独库公路",
            city = "伊犁",
            caption = "一天穿过四季，是独库公路的寻常操作。\n早晨还在草原上看羊群，下午就拐进了雪线。路边遇到骑摩托的姑娘，她说她已经在这条路上走了七天。",
            tags = listOf("独库公路", "新疆", "自驾"),
            imagePaths = listOf(asset("新疆.jpg")),
            bgmTitle = "原声 · 向北",
            baseLikes = 2310, baseComments = 188, baseShares = 402, favorites = 517,
            publishedAt = 1_763_232_000_000L
        ),
        MicroPost(
            id = "seed_bingdao",
            author = authors[7],
            place = "冰岛 · 维克黑沙滩",
            city = "雷克雅未克",
            caption = "等了三个晚上，极光终于出现了。\n风大到站不稳，相机架在冰里。绿色从海平线一点点爬上来的时候，整个人都安静了。附一份七天环岛路线在评论区。",
            tags = listOf("冰岛", "极光", "环岛自驾"),
            imagePaths = listOf(asset("冰岛.jpg"), asset("冰岛.jpg")),
            bgmTitle = "原声 · 山谷风",
            baseLikes = 5820, baseComments = 431, baseShares = 1102, favorites = 1340,
            publishedAt = 1_763_148_000_000L
        ),
        MicroPost(
            id = "seed_dali",
            author = authors[4],
            place = "丽江 · 玉龙雪山",
            city = "丽江",
            caption = "雪山脚下发呆的一下午。\n没有赶景点，就在蓝月谷边坐着。水面是那种不真实的多巴胺蓝，云影一过，整座山都变了颜色。",
            tags = listOf("丽江", "玉龙雪山", "慢旅行"),
            imagePaths = listOf(asset("丽江.jpg")),
            bgmTitle = "原声 · 一只鹿",
            baseLikes = 654, baseComments = 52, baseShares = 88, favorites = 121,
            publishedAt = 1_763_064_000_000L
        ),
        MicroPost(
            id = "seed_sanya",
            author = authors[5],
            place = "三亚 · 后海村",
            city = "三亚",
            caption = "冲浪小白的第三天，终于站起来了五秒钟。\n后海村的黄昏比想象中温柔，浪不大，夕阳把每个人都染成金色。晚上在村口吃清补凉，快乐很简单。",
            tags = listOf("三亚", "冲浪", "海边生活"),
            imagePaths = listOf(asset("三亚.jpg"), asset("三亚.jpg")),
            bgmTitle = "原声 · 小满",
            baseLikes = 1208, baseComments = 96, baseShares = 143, favorites = 220,
            publishedAt = 1_762_980_000_000L
        ),
        MicroPost(
            id = "seed_kyoto",
            author = authors[6],
            place = "京都 · 岚山竹林",
            city = "京都",
            caption = "清晨六点的岚山，才是京都该有的样子。\n游客还没来，竹叶上的露水在响。走到竹林尽头遇见一座小寺，住持在扫院子，说「今天风很好」。",
            tags = listOf("京都", "岚山", "日本旅行"),
            imagePaths = listOf(asset("京都.jpg")),
            bgmTitle = "原声 · 陈屿",
            baseLikes = 1876, baseComments = 143, baseShares = 265, favorites = 402,
            publishedAt = 1_762_896_000_000L
        ),
        MicroPost(
            id = "seed_daocheng",
            author = authors[2],
            place = "稻城亚丁 · 牛奶海",
            city = "甘孜",
            caption = "海拔4600米的牛奶海，走完最后那段路的时候真的哭了。\n高反、暴晒、碎石路，所有抱怨在看见那片蓝的瞬间全部作废。身体在地狱，眼睛在天堂这句话是真的。",
            tags = listOf("稻城亚丁", "徒步", "高原"),
            imagePaths = listOf(asset("稻城亚丁.jpg"), asset("西藏.jpg")),
            bgmTitle = "原声 · 麦子",
            baseLikes = 4306, baseComments = 327, baseShares = 781, favorites = 998,
            publishedAt = 1_762_812_000_000L
        ),
        MicroPost(
            id = "seed_changsha",
            author = authors[0],
            place = "长沙 · 湘江边",
            city = "长沙",
            caption = "长沙三天的真实记录：茶颜悦色喝了九杯，橘子洲走到腿软。\n江边的风很舒服，夜里灯光亮起来，整座城市都在喧闹，但一个人坐着也不觉得孤独。",
            tags = listOf("长沙", "美食", "周末旅行"),
            imagePaths = listOf(asset("长沙.jpg")),
            bgmTitle = "原声 · 阿茶",
            baseLikes = 733, baseComments = 61, baseShares = 97, favorites = 84,
            publishedAt = 1_762_728_000_000L
        )
    )
}
