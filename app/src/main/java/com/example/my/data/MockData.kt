package com.example.my.data

import com.example.my.R
import com.example.my.model.AppNotification
import com.example.my.model.Comment
import com.example.my.model.CommentReply
import com.example.my.model.CostItem
import com.example.my.model.DayPlan
import com.example.my.model.Footprint
import com.example.my.model.Guide
import com.example.my.model.Medal
import com.example.my.model.NotifType
import com.example.my.model.Route
import com.example.my.model.Spot
import com.example.my.model.UserData
import com.example.my.model.UserSettings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WbSunny

/** 应用内全部演示数据（对应网页端 MOCK_DATA） */
object MockData {

    /** 攻略额外配图（第 2、3 张真实景点照片），用于顶部轮播与全屏看图 */
    fun coverExtras(slug: String): List<Int> = when (slug) {
        "chongqing" -> listOf(R.drawable.ic_guide_chongqing_2, R.drawable.ic_guide_chongqing_3, R.drawable.ic_guide_chongqing_xhs_2, R.drawable.ic_guide_chongqing_xhs_3)
        "xian" -> listOf(R.drawable.ic_guide_xian_2, R.drawable.ic_guide_xian_3, R.drawable.ic_guide_xian_xhs_2, R.drawable.ic_guide_xian_xhs_3)
        "quanzhou" -> listOf(R.drawable.ic_guide_quanzhou_2, R.drawable.ic_guide_quanzhou_3)
        "dali" -> listOf(R.drawable.ic_guide_dali_2, R.drawable.ic_guide_dali_3)
        "tibet" -> listOf(R.drawable.ic_guide_tibet_2, R.drawable.ic_guide_tibet_3)
        "jiuzhaigou" -> listOf(R.drawable.ic_guide_jiuzhaigou_2, R.drawable.ic_guide_jiuzhaigou_3)
        "zhangjiajie" -> listOf(R.drawable.ic_guide_zhangjiajie_2, R.drawable.ic_guide_zhangjiajie_3)
        "huangshan" -> listOf(R.drawable.ic_guide_huangshan_2, R.drawable.ic_guide_huangshan_3)
        "dunhuang" -> listOf(R.drawable.ic_guide_dunhuang_2, R.drawable.ic_guide_dunhuang_3)
        "xiamen" -> listOf(R.drawable.ic_guide_xiamen_2, R.drawable.ic_guide_xiamen_3, R.drawable.ic_guide_xiamen_xhs_2, R.drawable.ic_guide_xiamen_xhs_3)
        "beihai" -> listOf(R.drawable.ic_guide_beihai_2, R.drawable.ic_guide_beihai_3)
        "zhouzhuang" -> listOf(R.drawable.ic_guide_zhouzhuang_2, R.drawable.ic_guide_zhouzhuang_3, R.drawable.ic_guide_zhouzhuang_4)
        "kanas" -> listOf(R.drawable.ic_guide_kanas_2, R.drawable.ic_guide_kanas_3)
        "lugu" -> listOf(R.drawable.ic_guide_lugu_2, R.drawable.ic_guide_lugu_3, R.drawable.ic_guide_lugu_4)
        "xuexiang" -> listOf(R.drawable.ic_guide_xuexiang_2, R.drawable.ic_guide_xuexiang_3)
        "qingcheng" -> listOf(R.drawable.ic_guide_qingcheng_2, R.drawable.ic_guide_qingcheng_3)
        "guilin" -> listOf(R.drawable.ic_guide_guilin_2, R.drawable.ic_guide_guilin_3)
        "changsha" -> listOf(R.drawable.ic_guide_changsha_2, R.drawable.ic_guide_changsha_3)
        "nanjing" -> listOf(R.drawable.ic_guide_nanjing_2, R.drawable.ic_guide_nanjing_3)
        "hangzhou" -> listOf(R.drawable.ic_guide_hangzhou_2, R.drawable.ic_guide_hangzhou_3, R.drawable.ic_guide_hangzhou_xhs_2, R.drawable.ic_guide_hangzhou_xhs_3)
        "suzhou" -> listOf(R.drawable.ic_guide_suzhou_2, R.drawable.ic_guide_suzhou_3)
        "luoyang" -> listOf(R.drawable.ic_guide_luoyang_2, R.drawable.ic_guide_luoyang_3)
        "wuyuan" -> listOf(R.drawable.ic_guide_wuyuan_2, R.drawable.ic_guide_wuyuan_3)
        "fenghuang" -> listOf(R.drawable.ic_guide_fenghuang_2, R.drawable.ic_guide_fenghuang_3)
        "huangguoshu" -> listOf(R.drawable.ic_guide_huangguoshu_2, R.drawable.ic_guide_huangguoshu_3)
        "xishuangbanna" -> listOf(R.drawable.ic_guide_xishuangbanna_2, R.drawable.ic_guide_xishuangbanna_3)
        "qingdao" -> listOf(R.drawable.ic_guide_qingdao_2, R.drawable.ic_guide_qingdao_3)
        "yangzhou" -> listOf(R.drawable.ic_guide_yangzhou_2, R.drawable.ic_guide_yangzhou_3)
        "qinghaihu" -> listOf(R.drawable.ic_guide_qinghaihu_2, R.drawable.ic_guide_qinghaihu_3)
        "hulunbuir" -> listOf(R.drawable.ic_guide_hulunbuir_2, R.drawable.ic_guide_hulunbuir_3)
        "pingyao" -> listOf(R.drawable.ic_guide_pingyao_2, R.drawable.ic_guide_pingyao_3)
        "wudang" -> listOf(R.drawable.ic_guide_wudang_2, R.drawable.ic_guide_wudang_3)
        "dalian" -> listOf(R.drawable.ic_guide_dalian_2, R.drawable.ic_guide_dalian_3)
        "nanchang" -> listOf(R.drawable.ic_guide_nanchang_2, R.drawable.ic_guide_nanchang_3)
        "wugongshan" -> listOf(R.drawable.ic_guide_wugongshan_2, R.drawable.ic_guide_wugongshan_3)
        "libo" -> listOf(R.drawable.ic_guide_libo_2, R.drawable.ic_guide_libo_3)
        "hukou" -> listOf(R.drawable.ic_guide_hukou_2, R.drawable.ic_guide_hukou_3)
        "arxan" -> listOf(R.drawable.ic_guide_arxan_2, R.drawable.ic_guide_arxan_3)
        "pingtan" -> listOf(R.drawable.ic_guide_pingtan_2, R.drawable.ic_guide_pingtan_3)
        "xichang" -> listOf(R.drawable.ic_guide_xichang_2, R.drawable.ic_guide_xichang_3)
        "mohe" -> listOf(R.drawable.ic_guide_mohe_2, R.drawable.ic_guide_mohe_3)
        "wanfenglin" -> listOf(R.drawable.ic_guide_wanfenglin_2, R.drawable.ic_guide_wanfenglin_3)
        "xitang" -> listOf(R.drawable.ic_guide_xitang_2, R.drawable.ic_guide_xitang_3)
        "daocheng" -> listOf(R.drawable.ic_guide_daocheng_2, R.drawable.ic_guide_daocheng_3)
        "detian" -> listOf(R.drawable.ic_guide_detian_2, R.drawable.ic_guide_detian_3)
        "shaoxing" -> listOf(R.drawable.ic_guide_shaoxing_2, R.drawable.ic_guide_shaoxing_3)
        else -> emptyList()
    }

    /** 额外图的来源标注（与 coverExtras 返回值一一对应；空串表示官方/无标注） */
    fun coverSources(slug: String): List<String> = when (slug) {
        "chongqing" -> listOf("", "", "小红书@LU.LU.LU", "小红书@Loading......")
        "xian" -> listOf("", "", "小红书@小文哥企服", "小红书@粉巷有棵柿子树")
        "hangzhou" -> listOf("", "", "小红书@星语心愿", "小红书@四季奶青三分糖")
        "xiamen" -> listOf("", "", "小红书@杂谈工作室", "小红书@妖灵")
        else -> emptyList()
    }
    val guides: List<Guide> = run {
        val old = originalGuides()
        old + buildGuideList(old.size)
    }

    private data class Classic(val title: String, val city: String, val days: Int, val region: String, val slug: String)

    /** 经典城市攻略：恢复原有带图攻略，内容保持不变，封面使用本地 drawable 实景图 */
    private fun originalGuides(): List<Guide> {
        // title, city, days, region, slug
        val classics = listOf(
            Classic("西安·长安十二时辰", "西安", 4, "西北", "xian"),
            Classic("北京·故宫与胡同秋", "北京", 3, "华北", "taishan"),
            Classic("杭州·西湖烟雨", "杭州", 3, "华东", "hangzhou"),
            Classic("苏州·园林姑苏", "苏州", 3, "华东", "suzhou"),
            Classic("南京·梧桐秋忆", "南京", 3, "华东", "nanjing"),
            Classic("上海·外滩蓝调", "上海", 2, "华东", "qingdao"),
            Classic("厦门·鼓浪屿慢时光", "厦门", 3, "华东", "xiamen"),
            Classic("泉州·半城烟火", "泉州", 3, "华东", "quanzhou"),
            Classic("青岛·红瓦绿树", "青岛", 3, "华东", "qingdao"),
            Classic("大连·滨海之城", "大连", 2, "东北", "dalian"),
            Classic("哈尔滨·冰雪大世界", "哈尔滨", 3, "东北", "harbin"),
            Classic("洛阳·牡丹与古城", "洛阳", 2, "华中", "luoyang"),
            Classic("武汉·大江大湖", "武汉", 2, "华中", "wuhan"),
            Classic("长沙·烟火人间", "长沙", 2, "华中", "changsha"),
            Classic("张家界·峰林惊奇", "张家界", 4, "华中", "zhangjiajie"),
            Classic("凤凰古城·边城", "湘西", 2, "华中", "fenghuang"),
            Classic("广州·早茶与都市", "广州", 2, "华南", "shunde"),
            Classic("深圳·山海之城", "深圳", 2, "华南", "qingdao"),
            Classic("珠海·情侣路", "珠海", 2, "华南", "weihai"),
            Classic("桂林·漓江山水", "桂林", 3, "华南", "guilin"),
            Classic("三亚·海岛度假", "三亚", 5, "华南", "sanya"),
            Classic("重庆·8D魔幻山城", "重庆", 3, "西南", "chongqing"),
            Classic("成都·巴蜀慢生活", "成都", 3, "西南", "chongqing"),
            Classic("昆明·春城飞花", "昆明", 3, "西南", "kunming"),
            Classic("大理·风花雪月", "大理", 4, "西南", "lijiang"),
            Classic("丽江·古城与雪山", "丽江", 4, "西南", "lijiang"),
            Classic("西双版纳·热带雨林", "西双版纳", 4, "西南", "xishuangbanna"),
            Classic("贵州·黔东南山水", "贵阳", 4, "西南", "guizhou"),
            Classic("稻城亚丁·蓝色星球", "甘孜", 5, "西南", "daocheng"),
            Classic("九寨沟·人间瑶池", "阿坝", 4, "西南", "jiuzhaigou"),
            Classic("西藏·朝圣八天", "拉萨", 8, "西南", "tibet"),
            Classic("敦煌·丝路莫高窟", "敦煌", 3, "西北", "dunhuang"),
            Classic("新疆·北疆环线", "乌鲁木齐", 7, "西北", "xinjiang"),
            Classic("额济纳·胡杨林", "阿拉善", 3, "西北", "ejina"),
            Classic("呼伦贝尔·草原牧歌", "呼伦贝尔", 5, "华北", "hulunbuir"),
            Classic("平遥·晋商古城", "晋中", 2, "华北", "pingyao"),
            Classic("武功山·云海草甸", "萍乡", 2, "华东", "wugongshan"),
            Classic("黄山·云海奇松", "黄山", 3, "华东", "huangshan"),
            Classic("宏村·徽派画里乡村", "黄山", 2, "华东", "huangshan"),
            Classic("婺源·篁岭晒秋", "上饶", 2, "华东", "wuyuan"),
            Classic("景德镇·瓷都手作", "景德镇", 2, "华东", "jingdezhen"),
            Classic("潮州·工夫茶与古城", "潮州", 2, "华南", "chaozhou"),
            Classic("顺德·世界美食之都", "佛山", 2, "华南", "shunde"),
            Classic("香港·维港夜色", "香港", 3, "华南", "hongkong"),
            Classic("澳门·烟火与葡韵", "澳门", 2, "华南", "macau"),
            Classic("威海·海滨干净小城", "威海", 3, "华东", "weihai"),
            Classic("千岛湖·湖光山色", "杭州", 2, "华东", "qiandaohu"),
            Classic("神农架·原始森林", "神农架", 3, "华中", "shennongjia"),
            Classic("京都·樱花物语", "京都", 5, "海外", "kyoto"),
            Classic("东京·都市漫游", "东京", 5, "海外", "tokyo"),
            Classic("冰岛·极光环岛", "冰岛", 8, "海外", "iceland"),
            Classic("宁波·浙东山海", "宁波", 2, "华东", "ningbo")
        )
        return classics.mapIndexed { i, c ->
            val (title, city, days, region, slug) = c
            val rid = when (slug) {
                "xian" -> R.drawable.ic_guide_xian
                "taishan" -> R.drawable.ic_guide_taishan
                "hangzhou" -> R.drawable.ic_guide_hangzhou
                "suzhou" -> R.drawable.ic_guide_suzhou
                "nanjing" -> R.drawable.ic_guide_nanjing
                "qingdao" -> R.drawable.ic_guide_qingdao
                "xiamen" -> R.drawable.ic_guide_xiamen
                "quanzhou" -> R.drawable.ic_guide_quanzhou
                "dalian" -> R.drawable.ic_guide_dalian
                "harbin" -> R.drawable.ic_guide_harbin
                "luoyang" -> R.drawable.ic_guide_luoyang
                "wuhan" -> R.drawable.ic_guide_wuhan
                "changsha" -> R.drawable.ic_guide_changsha
                "zhangjiajie" -> R.drawable.ic_guide_zhangjiajie
                "fenghuang" -> R.drawable.ic_guide_fenghuang
                "shunde" -> R.drawable.ic_guide_shunde
                "weihai" -> R.drawable.ic_guide_weihai
                "guilin" -> R.drawable.ic_guide_guilin
                "sanya" -> R.drawable.ic_guide_sanya
                "chongqing" -> R.drawable.ic_guide_chongqing
                "kunming" -> R.drawable.ic_guide_kunming
                "lijiang" -> R.drawable.ic_guide_lijiang
                "xishuangbanna" -> R.drawable.ic_guide_xishuangbanna
                "guizhou" -> R.drawable.ic_guide_guizhou
                "daocheng" -> R.drawable.ic_guide_daocheng
                "jiuzhaigou" -> R.drawable.ic_guide_jiuzhaigou
                "tibet" -> R.drawable.ic_guide_tibet
                "dunhuang" -> R.drawable.ic_guide_dunhuang
                "xinjiang" -> R.drawable.ic_guide_xinjiang
                "ejina" -> R.drawable.ic_guide_ejina
                "hulunbuir" -> R.drawable.ic_guide_hulunbuir
                "pingyao" -> R.drawable.ic_guide_pingyao
                "wugongshan" -> R.drawable.ic_guide_wugongshan
                "huangshan" -> R.drawable.ic_guide_huangshan
                "wuyuan" -> R.drawable.ic_guide_wuyuan
                "jingdezhen" -> R.drawable.ic_guide_jingdezhen
                "chaozhou" -> R.drawable.ic_guide_chaozhou
                "hongkong" -> R.drawable.ic_guide_hongkong
                "macau" -> R.drawable.ic_guide_macau
                "qiandaohu" -> R.drawable.ic_guide_qiandaohu
                "shennongjia" -> R.drawable.ic_guide_shennongjia
                "kyoto" -> R.drawable.ic_guide_kyoto
                "tokyo" -> R.drawable.ic_guide_tokyo
                "iceland" -> R.drawable.ic_guide_iceland
                "ningbo" -> R.drawable.ic_guide_ningbo
                else -> null
            }
            Guide(
                id = i + 1,
                title = title,
                city = city,
                days = days,
                type = "经典路线",
                author = "足迹攻略组",
                authorIntro = "城市经典目的地，行程成熟可直接复制",
                likes = 200 + (i * 211) % 6000,
                favs = 100 + (i * 131) % 4000,
                cost = "人均 ¥${800 + days * 260}",
                coverSeed = i + 1,
                tags = listOf(region, city, "经典"),
                intro = "${city}·${days}天经典路线，含核心景点与当地美食，适合首次前往。",
                costBreakdown = buildCostBreakdown(800 + days * 260),
                tips = listOf("提前预约热门景点门票。", "住宿选市中心或景区附近更方便。"),
                itinerary = run {
                    val core = title.substringBefore("·").substringBefore("·").take(12)
                    val virtualRoute = buildString {
                        append("Day1抵达${city}入住酒店；")
                        for (d in 2..days) {
                            append("Day${d}${core}深度游览");
                            if (d < days) append("；")
                        }
                    }
                    parseRoute(virtualRoute, days, "${city}当地特色小吃", "${city}中心连锁酒店约 ¥250/晚")
                },
                commentList = emptyList(),
                featured = i in setOf(0, 3, 20, 26, 31),
                theme = if (region == "海外") "人文历史" else "城市漫游",
                region = "",
                viewedCount = 3000 + (i * 911) % 50000,
                bannerTitle = title,
                bannerSubtitle = "经典 · ${days}天 · ${city}",
                coverResId = rid,
                images = listOfNotNull(rid) + coverExtras(slug),
                imageSources = listOf("") + coverSources(slug)
            )
        }
    }

    private data class RawGuide(
        val title: String,
        val city: String,
        val days: Int,
        val region: String,
        val theme: String,
        val kind: String,
        val route: String,
        val lodging: String,
        val food: String,
        val cost: Int,
        val tip: String,
        val tags: List<String>
    )

    private fun buildGuideList(offset: Int = 0): List<Guide> {
        // 每条新攻略对应的本地封面 drawable slug（与 50 条顺序一一对应）
        val coverSlugs = listOf(
            "chongqing", "xian", "quanzhou", "beihai", "dali", "tibet", "zhouzhuang",
            "jiuzhaigou", "kanas", "lugu", "xuexiang", "qingcheng", "guilin", "changsha",
            "nanjing", "hangzhou", "suzhou", "xiamen", "luoyang", "wuyuan", "fenghuang",
            "zhangjiajie", "huangguoshu", "xishuangbanna", "qingdao", "yangzhou", "dunhuang",
            "qinghaihu", "hulunbuir", "pingyao", "wudang", "huangshan", "huangshan", "dalian",
            "nanchang", "wugongshan", "dali", "libo", "hukou", "arxan", "pingtan", "xichang",
            "mohe", "wanfenglin", "xitang", "chongqing", "daocheng", "hangzhou", "detian",
            "shaoxing"
        )
        fun coverRes(slug: String): Int? = when (slug) {
            "chongqing" -> R.drawable.ic_guide_chongqing
            "xian" -> R.drawable.ic_guide_xian
            "quanzhou" -> R.drawable.ic_guide_quanzhou
            "beihai" -> R.drawable.ic_guide_beihai
            "dali" -> R.drawable.ic_guide_dali
            "tibet" -> R.drawable.ic_guide_tibet
            "zhouzhuang" -> R.drawable.ic_guide_zhouzhuang
            "jiuzhaigou" -> R.drawable.ic_guide_jiuzhaigou
            "kanas" -> R.drawable.ic_guide_kanas
            "lugu" -> R.drawable.ic_guide_lugu
            "xuexiang" -> R.drawable.ic_guide_xuexiang
            "qingcheng" -> R.drawable.ic_guide_qingcheng
            "guilin" -> R.drawable.ic_guide_guilin
            "changsha" -> R.drawable.ic_guide_changsha
            "nanjing" -> R.drawable.ic_guide_nanjing
            "hangzhou" -> R.drawable.ic_guide_hangzhou
            "suzhou" -> R.drawable.ic_guide_suzhou
            "xiamen" -> R.drawable.ic_guide_xiamen
            "luoyang" -> R.drawable.ic_guide_luoyang
            "wuyuan" -> R.drawable.ic_guide_wuyuan
            "fenghuang" -> R.drawable.ic_guide_fenghuang
            "zhangjiajie" -> R.drawable.ic_guide_zhangjiajie
            "huangguoshu" -> R.drawable.ic_guide_huangguoshu
            "xishuangbanna" -> R.drawable.ic_guide_xishuangbanna
            "qingdao" -> R.drawable.ic_guide_qingdao
            "yangzhou" -> R.drawable.ic_guide_yangzhou
            "dunhuang" -> R.drawable.ic_guide_dunhuang
            "qinghaihu" -> R.drawable.ic_guide_qinghaihu
            "hulunbuir" -> R.drawable.ic_guide_hulunbuir
            "pingyao" -> R.drawable.ic_guide_pingyao
            "wudang" -> R.drawable.ic_guide_wudang
            "huangshan" -> R.drawable.ic_guide_huangshan
            "dalian" -> R.drawable.ic_guide_dalian
            "nanchang" -> R.drawable.ic_guide_nanchang
            "wugongshan" -> R.drawable.ic_guide_wugongshan
            "libo" -> R.drawable.ic_guide_libo
            "hukou" -> R.drawable.ic_guide_hukou
            "arxan" -> R.drawable.ic_guide_arxan
            "pingtan" -> R.drawable.ic_guide_pingtan
            "xichang" -> R.drawable.ic_guide_xichang
            "mohe" -> R.drawable.ic_guide_mohe
            "wanfenglin" -> R.drawable.ic_guide_wanfenglin
            "xitang" -> R.drawable.ic_guide_xitang
            "daocheng" -> R.drawable.ic_guide_daocheng
            "detian" -> R.drawable.ic_guide_detian
            "shaoxing" -> R.drawable.ic_guide_shaoxing
            else -> null
        }
        val raw = listOf(
            RawGuide("重庆洪崖洞", "重庆", 1, "西南", "美食", "城市地标",
                "下午山城步道→白象居→傍晚千厮门大桥→晚上洪崖洞夜景",
                "解放碑附近连锁酒店220-280元/晚；不住宿可当日往返",
                "重庆小面、酸辣粉、冰粉、老火锅",
                260, "不要只挤洪崖洞内部，大桥上拍全景效果最好。",
                listOf("夜景", "火锅", "山城")),
            RawGuide("西安大雁塔·大唐不夜城", "西安", 2, "西北", "人文历史", "古都",
                "Day1陕西历史博物馆→大雁塔广场；Day2大唐不夜城夜游、永兴坊逛吃",
                "钟楼附近酒店230-290元/晚",
                "肉夹馍、甑糕、油泼面、羊肉泡馍",
                680, "陕历博务必提前预约；晚上不夜城人流大，保管随身物品。",
                listOf("古都", "夜景", "博物馆")),
            RawGuide("泉州古城", "泉州", 3, "华东", "人文历史", "古城",
                "Day1开元寺+西街+中山路骑楼；Day2洛阳桥+蟳埔簪花；Day3清净寺、关岳庙闲逛",
                "古城外围民宿190-250元/晚",
                "面线糊、姜母鸭、土笋冻、肉粽",
                850, "蟳埔簪花选街边小店性价比更高，避开景区高价店。",
                listOf("簪花", "海丝", "古巷")),
            RawGuide("北海银滩", "北海", 2, "华南", "自然风光", "海滨",
                "Day1银滩看日落→侨港风情街；Day2红树林赶海、老街闲逛",
                "银滩附近民宿200-270元/晚",
                "海鲜加工、虾饼、糖水",
                780, "海鲜尽量市场买再找店加工，景区餐厅价格偏高。",
                listOf("海滩", "赶海", "海鲜")),
            RawGuide("大理洱海", "大理", 3, "西南", "自然风光", "湖泊",
                "Day1大理古城+苍山；Day2磻溪S弯骑行、喜洲古镇；Day3双廊古镇、龙龛码头日落",
                "才村/古城民宿220-380元/晚",
                "烤乳扇、喜洲粑粑、砂锅鱼、白族土八碗",
                1350, "环洱海租电动车注意电量，不要强行跑全程。",
                listOf("洱海", "骑行", "古镇")),
            RawGuide("拉萨布达拉宫", "拉萨", 3, "西南", "人文历史", "高原",
                "Day1药王山机位、布宫广场、八廓街；Day2布达拉宫+大昭寺、色拉寺辩经；Day3西藏博物馆闲逛",
                "八廓街附近供氧酒店300-450元/晚；初上高原优先供氧房",
                "藏面、甜茶、牦牛肉、藏饺",
                1600, "抵达前2天不洗澡，布达拉宫门票需要提前抢预约。",
                listOf("布宫", "信仰", "高原")),
            RawGuide("周庄古镇", "昆山", 2, "华东", "人文历史", "水乡古镇",
                "Day1抵达，沈厅张厅，傍晚摇橹船，夜景灯笼；Day2清晨无人古镇，南湖，古戏台，买伴手礼",
                "古镇内临水民宿260-380元/晚；住景区内可享早晚无人时段",
                "万三蹄、阿婆菜、袜底酥、青团",
                950, "摇橹船可以拼船，降低单人开销。",
                listOf("水乡", "摇橹船", "古镇")),
            RawGuide("九寨沟", "阿坝", 3, "西南", "自然风光", "山水",
                "Day1抵达休整适应海拔；Day2日则沟五花海、珍珠滩、则查洼沟长海五彩池；Day3树正群海、扎如寺，返程",
                "沟口彭丰村酒店220-350元/晚",
                "藏式火锅、牦牛肉、青稞饼；景区内餐饮贵，自备干粮",
                1150, "一定要买观光车票；早起进沟避开旅行团；高反不要马上洗澡。",
                listOf("海子", "山水", "世界遗产")),
            RawGuide("喀纳斯湖", "阿勒泰", 4, "西北", "自然风光", "山林湖泊",
                "Day1布尔津出发抵达喀纳斯；Day2三湾（神仙湾、月亮湾、卧龙湾）；Day3登观鱼台俯瞰喀纳斯湖；Day4湖边漫步，返程",
                "景区木屋400-500元/晚；布尔津县城连锁酒店280-330元/晚",
                "烤包子、大盘鸡、羊肉汤",
                1800, "山区昼夜温差大，厚外套必备；备好高热量零食。",
                listOf("三湾", "秋景", "北疆")),
            RawGuide("泸沽湖", "丽江", 2, "西南", "自然风光", "湖泊",
                "Day1抵达，猪槽船游湖，草海走婚桥；Day2情人滩，女神湾看日落，返程",
                "尼塞村民宿240-320元/晚；里格半岛湖景民宿价格偏高",
                "摩梭石锅鱼、摩梭烤肉、烤土豆",
                920, "猪槽船提前谈好价格；雨季注意天气变化。",
                listOf("泸沽湖", "猪槽船", "摩梭")),
            RawGuide("雪乡", "牡丹江", 3, "东北", "自然风光", "冰雪",
                "Day1抵达，雪韵大街逛雪蘑菇；Day2梦幻家园、雪地摩托；晚上篝火晚会；Day3闲逛拍照，返程",
                "雪韵大街炕房民宿320-420元/晚；景区外性价比更高",
                "铁锅炖大鹅、小鸡炖蘑菇、锅包肉、冻梨",
                1300, "冬季严寒，做好全身保暖；雪地项目提前确认价格。",
                listOf("雪乡", "冰雪", "东北")),
            RawGuide("都江堰·青城山", "成都", 2, "西南", "人文历史", "山水人文",
                "Day1都江堰景区：鱼嘴、飞沙堰、宝瓶口；傍晚南桥夜景；Day2青城山前山人文/后山徒步二选一",
                "灌县古城周边民宿170-240元/晚",
                "尤兔头、渣渣面、青城老腊肉",
                720, "山区多雨，随身带雨伞；提前公众号购票。",
                listOf("水利", "道教", "青山")),
            RawGuide("桂林阳朔", "桂林", 3, "华南", "自然风光", "喀斯特山水",
                "Day1桂林市区，两江四湖；Day2漓江游船到阳朔；Day3遇龙河竹筏，十里画廊骑行",
                "阳朔西街民宿180-260元/晚",
                "桂林米粉、啤酒鱼、田螺酿",
                1050, "竹筏选正规渠道，不要找路边野筏；十里画廊骑行很舒服。",
                listOf("漓江", "竹筏", "喀斯特")),
            RawGuide("长沙橘子洲·岳麓山", "长沙", 2, "华中", "美食", "城市小吃",
                "Day1橘子洲头、太平街坡子街逛吃；Day2岳麓山爬山，湖南大学，省博物馆",
                "五一广场附近民宿160-240元/晚；青旅床位60-80元",
                "臭豆腐、糖油粑粑、剁椒鱼头、米粉",
                750, "湖南省博物馆必须提前预约；橘子洲可以步行代替观光车省钱。",
                listOf("小吃", "岳麓山", "博物馆")),
            RawGuide("南京", "南京", 3, "华东", "人文历史", "古都",
                "Day1中山陵、明孝陵；Day2夫子庙老门东；Day3南京博物院、鸡鸣寺玄武湖",
                "新街口/夫子庙附近酒店220-300元/晚",
                "盐水鸭、鸭血粉丝汤、牛肉锅贴",
                1100, "南京博物院提前预约；老门东比夫子庙小吃更地道。",
                listOf("古都", "博物馆", "梧桐")),
            RawGuide("杭州西湖", "杭州", 2, "华东", "城市漫游", "湖城",
                "Day1西湖环湖，断桥、苏堤、三潭印月坐船；Day2灵隐寺，龙井村喝茶",
                "湖滨/河坊街附近酒店230-320元/晚",
                "西湖醋鱼、杭帮菜、葱包烩、定胜糕",
                980, "西湖大部分免费，游船、灵隐需要购票；避开节假日高峰。",
                listOf("西湖", "龙井", "禅意")),
            RawGuide("苏州", "苏州", 3, "华东", "人文历史", "园林古城",
                "Day1拙政园、狮子林；Day2虎丘、山塘街夜景；Day3苏州博物馆、平江路闲逛",
                "平江路周边民宿200-280元/晚",
                "松鼠桂鱼、奥灶面、蟹黄汤包、桂花糕",
                1150, "苏博务必提前预约；山塘夜景傍晚最美。",
                listOf("园林", "苏博", "平江路")),
            RawGuide("厦门鼓浪屿", "厦门", 3, "华东", "城市漫游", "海岛",
                "Day1登鼓浪屿逛菽庄花园、日光岩；Day2环岛路骑行，沙坡尾；Day3曾厝垵闲逛",
                "鼓浪屿民宿260-380元；厦门本岛思明区酒店200-280元",
                "沙茶面、土笋冻、海蛎煎、花生汤",
                1300, "鼓浪屿船票提前抢；岛上物价偏高，零食自带一部分。",
                listOf("鼓浪屿", "海岛", "骑行")),
            RawGuide("洛阳老君山", "洛阳", 2, "华中", "自然风光", "山岳",
                "Day1抵达，山脚休整；Day2索道上山，金顶道观群，看云海，下山",
                "老君山脚下民宿160-240元；山上床位贵，尽量住山下",
                "洛阳水席、牛肉汤、烩菜",
                860, "山上温度低，带薄羽绒服；尽量早上山避开人流。",
                listOf("云海", "金顶", "道教")),
            RawGuide("婺源篁岭", "上饶", 2, "华东", "自然风光", "古村晒秋",
                "Day1篁岭晒秋古村，逛古建，夜景；Day2江岭梯田，李坑古村落闲逛",
                "篁岭山下民宿180-260元；住篁岭村内可以看清晨无人古村",
                "婺源汽糕、粉蒸肉、糊豆腐",
                920, "晒秋秋季最佳；春天看油菜花优先江岭。",
                listOf("晒秋", "油菜花", "古村")),
            RawGuide("凤凰古城", "湘西", 2, "华中", "人文历史", "边城古镇",
                "Day1沱江泛舟，逛古城，晚上沱江夜景；Day2南方长城可选，古城清晨闲逛",
                "沱江边临江民宿200-300元；非临江更便宜",
                "血粑鸭、苗家酸鱼、米豆腐",
                830, "古城免费进城，部分小景点收费；江边酒吧谨慎消费。",
                listOf("沱江", "吊脚楼", "夜景")),
            RawGuide("张家界", "张家界", 4, "华中", "自然风光", "石英砂岩峰林",
                "Day1抵达，武陵源；Day2森林公园，金鞭溪，袁家界；Day3天子山、十里画廊；Day4天门山",
                "武陵源景区门口酒店220-320元/晚",
                "三下锅、腊味、葛根粉",
                1450, "天门山门票务必提前预约；山上多雨带雨衣。",
                listOf("峰林", "天门山", "玻璃栈道")),
            RawGuide("黄果树瀑布", "安顺", 2, "西南", "自然风光", "瀑布",
                "Day1黄果树大瀑布，天星桥；Day2陡坡塘瀑布，返程",
                "黄果树景区附近酒店190-280元/晚",
                "丝娃娃、酸汤鱼、花江狗肉（可选）",
                940, "丰水期（6-8月）瀑布水量最大；一定要买观光车。",
                listOf("瀑布", "喀斯特", "贵州")),
            RawGuide("西双版纳告庄", "西双版纳", 3, "西南", "自然风光", "热带雨林",
                "Day1告庄星光夜市；Day2野象谷看大象；Day3曼听公园、总佛寺",
                "告庄西双景民宿220-320元/晚",
                "菠萝饭、舂鸡脚、傣味烧烤、泡鲁达",
                1250, "星光夜市晚上人多；野象谷尽量上午去，大象更活跃。",
                listOf("夜市", "傣味", "雨林")),
            RawGuide("青岛", "青岛", 3, "华北", "城市漫游", "海滨城市",
                "Day1栈桥，八大关，小鱼山；Day2小麦岛看海；Day3崂山（可选）",
                "市南区五四广场附近酒店210-300元/晚",
                "海鲜大排档、鲅鱼饺子、青岛啤酒、锅贴",
                1100, "吃海鲜尽量选明码标价店；海边风大带外套。",
                listOf("海滨", "啤酒", "八大关")),
            RawGuide("扬州瘦西湖", "扬州", 2, "华东", "城市漫游", "园林慢城",
                "Day1瘦西湖，个园；Day2东关街、皮市街，大运河博物馆",
                "东关街附近民宿180-260元/晚",
                "扬州炒饭、狮子头、烫干丝、蟹黄汤包",
                880, "春季3-4月景色最佳；大运河博物馆提前预约。",
                listOf("瘦西湖", "早茶", "园林")),
            RawGuide("敦煌莫高窟", "敦煌", 3, "西北", "人文历史", "石窟沙漠",
                "Day1莫高窟数字中心+实体洞窟；Day2鸣沙山月牙泉，日落骑骆驼；Day3阳关古城，逛敦煌市区",
                "敦煌市区酒店220-300元/晚；不要住沙漠露营（条件差）",
                "驴肉黄面、杏皮水、胡杨焖饼",
                1450, "莫高窟A类票一定要提前抢；鸣沙山做好防晒防沙。",
                listOf("莫高窟", "沙漠", "丝路")),
            RawGuide("青海湖", "海南州", 3, "西北", "自然风光", "高原湖泊",
                "Day1西宁出发，二郎剑景区；Day2环湖西路，黑马河看日出；Day3茶卡盐湖可选，返程",
                "黑马河帐篷150-220元；二郎剑湖景房450+元；优先住西宁条件更好",
                "手抓羊肉、牦牛酸奶、油饼",
                1350, "7-8月油菜花盛开；昼夜温差极大，厚外套必备。",
                listOf("青海湖", "油菜花", "盐湖")),
            RawGuide("呼伦贝尔草原", "呼伦贝尔", 5, "华北", "自然风光", "草原",
                "Day1海拉尔集合；Day2阿尔山森林公园；Day3草原骑马穿越；Day4呼伦湖；Day5满洲里夜景，返程",
                "海拉尔/满洲里酒店260-380元；草原蒙古包200-300元",
                "手把肉、烤羊腿、奶茶、奶皮子",
                2200, "夏季草原蚊虫多；骑马务必遵守向导要求。",
                listOf("草原", "骑马", "蒙古包")),
            RawGuide("平遥古城", "晋中", 2, "华北", "人文历史", "晋商古城",
                "Day1古城城墙，县衙，票号；晚上古城夜景；Day2周边王家大院，闲逛古城",
                "古城内晋商大院民宿180-280元/晚",
                "平遥牛肉、碗托、莜面栲栳栳",
                820, "古城进城免费，小景点联票；王家大院比乔家大院规模更大。",
                listOf("古城", "票号", "晋商")),
            RawGuide("武当山", "十堰", 2, "华中", "人文历史", "道教名山",
                "Day1武当山山门，太子坡，南岩宫；Day2金顶，下山返程",
                "武当山脚下酒店170-240元；山上住宿价格贵",
                "武当山野菜、道家素斋、郧阳三合汤",
                850, "金顶需要额外购票；爬山穿舒适鞋子。",
                listOf("武当", "金顶", "道教")),
            RawGuide("宏村", "黄山", 2, "华东", "人文历史", "徽派古村",
                "Day1宏村南湖、月沼，古民居，傍晚南湖日落；Day2塔川秋景（秋季），木梨硔，返程",
                "宏村村内徽派民宿190-270元/晚",
                "臭鳜鱼、毛豆腐、笋衣烧肉、黄山烧饼",
                860, "住村内可以看清晨无人宏村；秋季塔川红叶绝美。",
                listOf("徽派", "月沼", "秋景")),
            RawGuide("黄山", "黄山", 3, "华东", "自然风光", "山岳",
                "Day1抵达汤口，上山；Day2山上看日出，西海大峡谷；Day3下山，汤口休整",
                "汤口山下酒店180-260元；山上多人间床位180-280元（旺季涨价）",
                "黄山笋干、石耳炖鸡；山上餐饮很贵，自带干粮",
                1380, "山上天气多变，带雨衣；看日出查好日出时间。",
                listOf("黄山", "日出", "奇松")),
            RawGuide("大连", "大连", 2, "东北", "城市漫游", "海滨城市",
                "Day1星海广场，老虎滩；Day2棒棰岛，俄罗斯风情街",
                "中山区酒店200-280元/晚",
                "焖子、海鲜锅贴、铁板鱿鱼",
                840, "海边城市风大，夏季游玩体验最好。",
                listOf("海滨", "广场", "俄式风情")),
            RawGuide("南昌滕王阁", "南昌", 1, "华东", "人文历史", "名楼城市",
                "上午滕王阁，江西省博物馆；下午绳金塔；傍晚看滕王阁夜景",
                "当日往返；住八一广场附近酒店160-230元",
                "南昌拌粉、瓦罐汤、南昌水煮",
                380, "滕王阁夜景比白天好看；省博提前预约。",
                listOf("滕王阁", "赣菜", "夜景")),
            RawGuide("武功山", "萍乡", 2, "华东", "自然风光", "高山草甸",
                "Day1山脚出发徒步上山，山顶看日落；Day2山顶看日出，下山返程",
                "山顶帐篷120-200元；山脚酒店150-220元",
                "武功山土菜，笋干、腊肉；山顶餐饮贵，自带干粮",
                760, "徒步穿防滑鞋；山顶昼夜温差巨大，帐篷带厚睡袋。",
                listOf("草甸", "云海", "徒步")),
            RawGuide("大理双廊", "大理", 1, "西南", "自然风光", "洱海东岸",
                "抵达双廊古镇，玉几岛，南诏风情岛，傍晚海边看日落",
                "可当日往返；双廊民宿260-400元",
                "白族酸辣鱼、水性杨花",
                520, "南诏风情岛船票提前买；傍晚光线拍照极佳。",
                listOf("双廊", "洱海", "日落")),
            RawGuide("荔波小七孔", "黔南", 2, "西南", "自然风光", "喀斯特水景",
                "Day1小七孔古桥，卧龙潭，鸳鸯湖；Day2大七孔，返程",
                "小七孔西门民宿170-250元/晚",
                "酸汤牛肉、荔波杨梅汤、香猪",
                880, "夏季玩水，带拖鞋；景区很大，一定要坐观光车。",
                listOf("小七孔", "碧水", "喀斯特")),
            RawGuide("壶口瀑布", "延安", 1, "华北", "自然风光", "黄河瀑布",
                "抵达壶口景区，游览瀑布，看黄河奔腾，下午返程",
                "当日往返；住宜川县城酒店160-220元",
                "黄河鲤鱼、陕北抿节、羊肉面",
                460, "汛期7-8月水量最大；风大，做好防护。",
                listOf("黄河", "瀑布", "陕北")),
            RawGuide("阿尔山", "兴安盟", 3, "华北", "自然风光", "森林温泉",
                "Day1阿尔山森林公园，天池；Day2不冻河，三潭峡；Day3白狼峰，返程",
                "阿尔山市区酒店240-340元；森林公园内木屋价格偏高",
                "东北铁锅炖、山野菜、奶茶",
                1420, "秋季9月景色绝美；山区气温低，厚外套必备。",
                listOf("天池", "森林", "秋景")),
            RawGuide("平潭岛", "福州", 2, "华东", "自然风光", "海岛",
                "Day1龙王头沙滩，坛南湾；Day2长江澳风车田，猴研岛打卡",
                "坛南湾民宿200-280元/晚",
                "平潭海鲜、时来运转、海蛎饼",
                850, "蓝眼泪看运气；岛上租电动车出行方便。",
                listOf("海岛", "蓝眼泪", "风车田")),
            RawGuide("西昌邛海", "西昌", 2, "西南", "自然风光", "湖泊温泉",
                "Day1邛海环湖骑行，泸山；Day2螺髻山泡温泉，建昌古城闲逛",
                "邛海附近民宿180-260元/晚",
                "西昌火盆烧烤、醉虾、卷粉",
                870, "螺髻山温泉建议下午去；环湖骑行体验很棒。",
                listOf("邛海", "烧烤", "温泉")),
            RawGuide("漠河北极村", "漠河", 4, "东北", "自然风光", "极北冰雪",
                "Day1抵达漠河；Day2九曲十八弯，白桦林；Day3北极村，打卡中国最北；Day4北红村，返程",
                "北极村民宿炕房220-320元；漠河县城酒店180-260元",
                "铁锅炖、东北杀猪菜、冷水鱼",
                1650, "冬季零下三十多度，全套防寒装备；找正规包车。",
                listOf("北极", "极光", "白桦林")),
            RawGuide("兴义万峰林", "黔西南", 2, "西南", "自然风光", "喀斯特田园",
                "Day1万峰林，田园骑行，八卦田；Day2马岭河峡谷，返程",
                "下纳灰村民宿170-240元/晚",
                "兴义刷把头、鸡肉汤圆、酸笋牛肉",
                780, "春天油菜花，秋天稻田金黄；租电动车逛田园很舒服。",
                listOf("峰林", "田园", "喀斯特")),
            RawGuide("西塘古镇", "嘉兴", 2, "华东", "人文历史", "水乡古镇",
                "Day1抵达，逛西塘古街，烟雨长廊，晚上灯笼夜景；Day2清晨无人古镇，坐摇橹船，返程",
                "古镇内临水民宿200-280元/晚",
                "芡实糕、粉蒸肉、黄酒、酱鸭",
                810, "住景区内，早晚避开旅行团；周末人非常多。",
                listOf("水乡", "烟雨长廊", "灯笼")),
            RawGuide("武隆天生三桥", "重庆", 1, "西南", "自然风光", "天坑地缝",
                "全天逛天生三桥，龙水峡地缝，下午返程",
                "当日往返；武隆县城酒店160-230元",
                "武隆碗碗羊肉、羊角豆干、土家腊肉",
                450, "景区台阶多，穿舒适鞋子；一定要买观光车。",
                listOf("天坑", "地缝", "喀斯特")),
            RawGuide("稻城亚丁", "甘孜", 5, "西南", "自然风光", "高原雪山",
                "Day1抵达香格里拉镇适应海拔；Day2亚丁短线：珍珠海冲古寺；Day3亚丁长线：洛绒牛场、五色海牛奶海；Day4休整；Day5返程",
                "香格里拉镇酒店260-360元；不建议住亚丁村（高反风险）",
                "藏餐、牦牛肉汤锅；景区物资贵，自备干粮",
                2100, "高反风险很高；不要剧烈跑动；长线徒步体力消耗大。",
                listOf("亚丁", "雪山", "徒步")),
            RawGuide("桐庐富春江", "杭州", 2, "华东", "自然风光", "江南山水",
                "Day1富春江游船，严子陵钓台；Day2芦茨古村，大奇山森林公园",
                "芦茨村民宿190-270元/晚",
                "富春江鱼、桐庐土菜、米粿",
                830, "适合休闲放松，节奏慢，适合家庭出游。",
                listOf("富春江", "古村", "亲子")),
            RawGuide("崇左德天瀑布", "崇左", 2, "华南", "自然风光", "跨国瀑布",
                "Day1德天跨国瀑布，逛界河；Day2明仕田园竹筏，田园骑行",
                "硕龙镇酒店180-250元/晚",
                "壮乡五色糯米饭、酸粥、河鱼",
                860, "丰水期6-10月瀑布最壮观；明仕田园竹筏体验很好。",
                listOf("瀑布", "竹筏", "边境")),
            RawGuide("绍兴", "绍兴", 2, "华东", "人文历史", "江南古城",
                "Day1鲁迅故里，沈园夜游；Day2东湖乌篷船，安昌古镇",
                "鲁迅故里附近民宿180-260元/晚",
                "绍兴黄酒、茴香豆、梅干菜扣肉、黄酒棒冰",
                820, "沈园夜景值得一看；安昌古镇腊月年味十足。",
                listOf("鲁迅", "黄酒", "乌篷船"))
        )
        return raw.mapIndexed { index, r ->
            val id = offset + index + 1
            Guide(
                id = id,
                title = r.title,
                city = r.city,
                days = r.days,
                type = r.kind,
                author = "足迹攻略组",
                authorIntro = "精选国内目的地，行程精简可直接复制使用",
                likes = 180 + (id * 173) % 5200,
                favs = 90 + (id * 97) % 3600,
                cost = "人均 ¥${r.cost}",
                coverSeed = id,
                tags = r.tags,
                intro = "${r.city}·${r.days}天${r.kind}路线，人均约 ¥${r.cost}（不含往返大交通）。${r.tip}",
                costBreakdown = buildCostBreakdown(r.cost),
                tips = listOf(
                    r.tip,
                    "住宿参考：${r.lodging}",
                    "必吃：${r.food}"
                ),
                itinerary = parseRoute(r.route, r.days, r.food, r.lodging),
                commentList = emptyList(),
                featured = id in setOf(offset + 1, offset + 2, offset + 5, offset + 6, offset + 8, offset + 13, offset + 17),
                theme = r.theme,
                region = r.region,
                viewedCount = 2600 + (id * 811) % 46000,
                bannerTitle = r.title,
                bannerSubtitle = "${r.region} · ${r.days}天 · 人均¥${r.cost}",
                coverResId = coverRes(coverSlugs[index]),
                images = listOfNotNull(coverRes(coverSlugs[index])) + coverExtras(coverSlugs[index]),
                imageSources = listOf("") + coverSources(coverSlugs[index])
            )
        }
    }

    private fun buildCostBreakdown(total: Int): List<CostItem> {
        val stay = (total * 0.34).toInt()
        val food = (total * 0.30).toInt()
        val ticket = (total * 0.16).toInt()
        val traffic = (total * 0.15).toInt()
        val other = total - stay - food - ticket - traffic
        return listOf(
            CostItem("住宿", "¥$stay"),
            CostItem("餐饮", "¥$food"),
            CostItem("门票", "¥$ticket"),
            CostItem("当地交通", "¥$traffic"),
            CostItem("其他", "¥${maxOf(other, 0)}"),
            CostItem("总计", "¥$total", total = true)
        )
    }

    /** 根据景点名猜测花费类型与金额：门票类给景区票价，餐饮类给餐标，免费地标 ¥0 */
    private fun guessCost(name: String): Pair<String, String> {
        val ticketKw = listOf("博物馆", "寺", "宫", "陵", "庙", "瀑", "湖", "山", "古镇", "古城",
            "岛", "草原", "沟", "峰", "峡", "林", "台", "关", "园", "窟", "堰", "楼", "塔", "桥",
            "碑", "沙", "冰川", "温泉", "索道", "船", "门票")
        val foodKw = listOf("美食", "好吃", "夜市", "坊", "铺", "小吃", "火锅", "小面", "街",
            "吃", "菜", "食", "墟", "市")
        return when {
            ticketKw.any { name.contains(it) } -> {
                val price = when {
                    name.contains("博物馆") -> 50 + name.length * 3
                    name.contains("寺") || name.contains("宫") || name.contains("陵") -> 60 + name.length * 2
                    name.contains("瀑") || name.contains("峡") || name.contains("冰川") -> 90 + name.length * 3
                    name.contains("山") || name.contains("峰") || name.contains("草原") -> 70 + name.length * 2
                    else -> 55 + name.length * 2
                }
                "门票约 ¥${price.coerceIn(40, 220)}" to "含景区门票，建议提前在线购票"
            }
            foodKw.any { name.contains(it) } -> {
                val price = 35 + (name.length % 4) * 15
                "餐饮约 ¥$price" to "当地特色，人均消费参考"
            }
            else -> "¥0" to "免费开放，打卡拍照"
        }
    }

    /** 智能解析 route 字符串为多天行程：每天多个景点，每个景点有名称/描述/花费 */
    private fun parseRoute(route: String, totalDays: Int, food: String, lodging: String): List<DayPlan> {
        val marker = Regex("Day(\\d+)")
        val matches = marker.findAll(route).toList()
        val rawDays: List<Pair<Int, String>> = if (matches.isEmpty()) {
            // 无 Day 标记：按 ； 或 ; 或换行切分；若只有一段则整天
            val segs = route.split(Regex("[；;\\n]")).map { it.trim() }.filter { it.isNotEmpty() }
            if (segs.isEmpty()) listOf(1 to route.trim())
            else segs.mapIndexed { i, s -> (i + 1) to s }
        } else {
            matches.mapIndexed { i, m ->
                val dayNum = m.groupValues[1].toIntOrNull() ?: (i + 1)
                val start = m.range.last + 1
                val end = matches.getOrNull(i + 1)?.range?.first ?: route.length
                dayNum to route.substring(start, end).trim().trim('，', '、', '；', ';', ' ', '。', ':')
            }
        }

        fun dayTitle(day: Int, total: Int): String = when {
            day == 1 && total > 1 -> "抵达与市区初印象"
            day == total && total > 1 -> "深度慢逛与返程"
            total == 1 -> "一日精华路线"
            else -> "核心景区深度游"
        }

        return rawDays.map { (dayNum, seg) ->
            // 每天内按 → 、 + ， 切分景点
            val spots = seg.split(Regex("[→、+，,]")).map { it.trim() }
                .filter { it.isNotEmpty() && it.length >= 2 }
                .map { raw ->
                    // 提取时间段前缀作为 note 前缀
                    val timeTag = Regex("^(上午|下午|傍晚|晚上|清晨|夜间|夜游|次日)").find(raw)
                    val name = timeTag?.let { raw.removeRange(it.range) } ?: raw
                    val (cost, notePrefix) = guessCost(name)
                    val note = buildString {
                        if (timeTag != null) append("${timeTag.value}，")
                        append(notePrefix)
                        if (dayNum == 1 && name.contains("酒店") || name.contains("入住")) {
                            append("；住宿参考 $lodging")
                        }
                    }
                    Spot(name = name.trim(), note = note, cost = cost, seed = dayNum * 31 + name.length)
                }
            // 每天末尾加一个餐饮 spot（从 food 字段取）
            val foodSpot = if (dayNum == rawDays.last().first) {
                Spot(name = "当地美食收尾", note = "推荐尝：$food", cost = "餐饮约 ¥50", seed = dayNum * 17 + 5)
            } else null
            val allSpots = spots.toMutableList()
            if (foodSpot != null) allSpots.add(foodSpot)
            DayPlan(day = dayNum, title = dayTitle(dayNum, totalDays), spots = allSpots)
        }
    }

    val routes = listOf(
        Route(
            id = 1,
            title = "西藏 · 纳木错星空之旅",
            year = 2024,
            date = "2024年8月",
            days = 6,
            cities = listOf("拉萨", "纳木错"),
            coverSeed = 3,
            special = true,
            specialName = "纳木错星空",
            specialReason = "凌晨两点的纳木错，银河横亘在湖面上空，风很冷，心很烫",
            coverResId = R.drawable.ic_guide_tibet,
            note = "深夜的纳木错，银河横亘在湖面上空。相机拍不出的美，都在眼睛里。",
            tags = listOf("星空", "高原", "自驾"),
            totalCost = "¥5,200",
            isPublic = true,
            photoCount = 18,
            itinerary = listOf(
                DayPlan(1, "拉萨集合 · 休整", listOf(
                    Spot("拉萨市区", "适应海拔，喝甜茶", "¥0")
                )),
                DayPlan(2, "布达拉宫 · 大昭寺", listOf(
                    Spot("布达拉宫", "预约进入，慢慢逛", "¥200"),
                    Spot("大昭寺", "转经道上的信仰", "¥85")
                )),
                DayPlan(3, "羊卓雍措", listOf(
                    Spot("羊卓雍措", "圣湖一日，蓝得不像话", "¥60")
                )),
                DayPlan(4, "纳木错 · 扎西半岛", listOf(
                    Spot("纳木错", "海拔 4718 米的星空之约", "¥120"),
                    Spot("扎西半岛", "日落与日出，圣湖对望神山", "¥0")
                ), special = true, specialReason = "凌晨两点，银河倒映在湖面上，此生难忘"),
                DayPlan(5, "返程拉萨", listOf(
                    Spot("当雄草原", "归途的草原风光", "¥0")
                ))
            )
        ),
        Route(
            id = 2,
            title = "大理 · 风花雪月四日",
            year = 2024,
            date = "2024年5月",
            days = 4,
            cities = listOf("大理"),
            coverSeed = 6,
            special = false,
            note = "洱海的风，苍山的雪，喜洲的稻田，都是大理写给旅人的情书。",
            tags = listOf("洱海", "骑行"),
            totalCost = "¥2,100",
            isPublic = false,
            photoCount = 12,
            itinerary = listOf(
                DayPlan(1, "大理古城", listOf(
                    Spot("大理古城", "人民路上的慢时光", "¥0")
                )),
                DayPlan(2, "环洱海骑行", listOf(
                    Spot("才村码头", "看日出", "¥0"),
                    Spot("喜洲古镇", "稻田与粑粑", "¥0")
                )),
                DayPlan(3, "苍山徒步", listOf(
                    Spot("苍山玉带路", "俯瞰洱海", "¥120")
                ))
            )
        ),
        Route(
            id = 3,
            title = "川西 · 稻城亚丁徒步",
            year = 2023,
            date = "2023年10月",
            days = 7,
            cities = listOf("成都", "稻城"),
            coverSeed = 7,
            special = true,
            specialName = "牛奶海",
            specialReason = "徒步四小时看到牛奶海的那一刻，所有疲惫都值得",
            coverResId = R.drawable.ic_guide_daocheng,
            note = "从洛绒牛场到牛奶海，海拔 4600 米的徒步，每一步都在跟身体谈判。",
            tags = listOf("徒步", "雪山"),
            totalCost = "¥4,800",
            isPublic = true,
            photoCount = 24,
            itinerary = listOf(
                DayPlan(1, "成都 → 稻城", listOf(
                    Spot("318 国道", "此生必驾的风景大道", "¥0")
                )),
                DayPlan(2, "亚丁村休整", listOf(
                    Spot("亚丁村", "适应海拔，雪山就在眼前", "¥0")
                )),
                DayPlan(3, "洛绒牛场 → 牛奶海", listOf(
                    Spot("牛奶海", "海拔 4600 米，牛奶色的圣湖", "¥0", "徒步 4 小时，备足氧气"),
                    Spot("五色海", "牛奶海之上的另一颗明珠", "¥0")
                ), special = true, specialReason = "徒步四小时看到牛奶海的那一刻，所有疲惫都值得"),
                DayPlan(4, "仙乃日神山", listOf(
                    Spot("仙乃日观景台", "神山脚下，心很静", "¥0")
                ))
            )
        ),
        Route(
            id = 4,
            title = "北京 · 秋日胡同漫步",
            year = 2022,
            date = "2022年11月",
            days = 3,
            cities = listOf("北京"),
            coverSeed = 4,
            special = false,
            note = "银杏落满胡同的秋天，是北京最温柔的季节。",
            tags = listOf("胡同", "银杏"),
            totalCost = "¥1,600",
            isPublic = false,
            photoCount = 9,
            itinerary = listOf(
                DayPlan(1, "故宫 · 景山", listOf(
                    Spot("故宫博物院", "红墙黄瓦的秋天", "¥60"),
                    Spot("景山公园", "俯瞰故宫全景", "¥2")
                )),
                DayPlan(2, "胡同银杏", listOf(
                    Spot("五道营胡同", "银杏与咖啡馆", "¥0"),
                    Spot("雍和宫", "秋色中的藏传佛教寺院", "¥25")
                ))
            )
        )
    )

    // 足迹地图 12 个标记点（坐标为地图坐标系）
    val footprints = listOf(
        Footprint(1, "乌鲁木齐", "乌鲁木齐 · 大巴扎", "中亚风情与烤包子香气", "2023年8月", 113.43f, 130.39f, seed = 1),
        Footprint(2, "拉萨", "拉萨 · 布达拉宫", "第一次仰望布宫，差点掉眼泪", "2024年8月", 151.55f, 357.28f, special = true, specialReason = "八天的朝圣之旅，从这里开始", seed = 3, routeId = 1),
        Footprint(3, "大理", "大理 · 洱海", "环海骑行的风都是甜的", "2024年5月", 250.67f, 423.25f, special = true, specialReason = "黄昏的洱海被染成金色", seed = 6, routeId = 2),
        Footprint(4, "成都", "成都 · 熊猫基地", "看滚滚打了一上午的架", "2023年10月", 293.15f, 342.8f, seed = 0, routeId = 3),
        Footprint(5, "重庆", "重庆 · 洪崖洞", "8D 魔幻城市的夜景", "2024年1月", 319.29f, 358.89f, seed = 2),
        Footprint(6, "西安", "西安 · 古城墙", "城墙骑行一圈，穿越千年", "2023年6月", 345.43f, 283.26f, seed = 8),
        Footprint(7, "三亚", "三亚 · 蜈支洲岛", "浮潜看到了海龟，值回票价", "2024年3月", 351.97f, 540.72f, special = true, specialReason = "小朋友第一次看鲨鱼看了一小时", seed = 8),
        Footprint(8, "广州", "广州 · 小蛮腰", "珠江夜游的灯光秀", "2023年4月", 393.36f, 463.48f, seed = 5),
        Footprint(9, "北京", "北京 · 故宫", "红墙黄瓦的秋日午后", "2022年11月", 427.12f, 193.14f, seed = 4, routeId = 4),
        Footprint(10, "上海", "上海 · 外滩", "蓝调时刻的万国建筑群", "2023年11月", 482.67f, 333.14f, seed = 5),
        Footprint(11, "杭州", "杭州 · 西湖", "烟雨朦胧的江南", "2024年4月", 468.51f, 347.62f, seed = 9),
        Footprint(12, "哈尔滨", "哈尔滨 · 冰雪大世界", "零下 25 度的冰与火", "2024年2月", 538.22f, 98.2f, seed = 1)
    )

    val user = UserData(
        nickname = "林屿",
        signature = "山高路远，看世界，也找自己",
        avatarText = "林",
        citiesCount = 12,
        footprintCount = 156,
        daysCount = 68,
        followers = 328,
        following = 87,
        likesReceived = 1256,
        medals = listOf(
            Medal("初心者", "记录第 1 段足迹", Icons.Outlined.Eco, unlocked = true, current = 1, target = 1),
            Medal("小有所成", "记录 10 段足迹", Icons.Outlined.Assignment, unlocked = true, current = 10, target = 10),
            Medal("旅行达人", "记录 50 段足迹", Icons.Outlined.CardTravel, unlocked = false, current = 15, target = 50),
            Medal("城市收集者", "走过 5 座城市", Icons.Outlined.LocationCity, unlocked = true, current = 5, target = 5),
            Medal("城市漫游家", "走过 20 座城市", Icons.Outlined.Explore, unlocked = false, current = 13, target = 20),
            Medal("环球行者", "去过 2 个国家", Icons.Outlined.Public, unlocked = true, current = 2, target = 2),
            Medal("星光收藏家", "标记 5 个特别地点", Icons.Outlined.Star, unlocked = true, current = 5, target = 5),
            Medal("四季行者", "春夏秋冬都有足迹", Icons.Outlined.WbSunny, unlocked = true, current = 4, target = 4),
            Medal("月月不停歇", "足迹覆盖 6 个月份", Icons.Outlined.CalendarMonth, unlocked = true, current = 6, target = 6)
        )
    )

    /** 那年今日（对应回忆页突出卡片） */
    val memoryToday = routes.first { it.id == 1 }

    /** 通知中心数据 */
    val notifications = listOf(
        AppNotification(1, NotifType.LIKE, "阿茶 赞了你的攻略", "《在京都，把秋天装进口袋》收获 1 个新赞", "2 分钟前"),
        AppNotification(2, NotifType.FOLLOW, "向北 关注了你", "很高兴认识你，一起去看世界吧", "1 小时前"),
        AppNotification(3, NotifType.COMMENT, "麦子 评论了你的足迹", "\"这张外滩的蓝调时刻拍得太绝了\"", "3 小时前"),
        AppNotification(4, NotifType.SYSTEM, "年度旅行报告已生成", "今年你共走过 8 座城市，68 天在路上", "昨天"),
        AppNotification(5, NotifType.LIKE, "小满 收藏了你的攻略", "《大理 7 日躺平指南》被加入收藏", "昨天", read = true),
        AppNotification(6, NotifType.SYSTEM, "新勋章解锁：风景猎人", "你的足迹累计获得 1000+ 赞", "3 天前", read = true),
        AppNotification(7, NotifType.COMMENT, "山风 回复了你的评论", "\"下次一起走川西环线吧！\"", "5 天前", read = true)
    )

    /** 默认设置 */
    val defaultSettings = UserSettings()
}
