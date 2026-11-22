package com.example.my.data.music

/**
 * 歌词简繁转换。
 *
 * 只做「常用字」级别的离线转换，不依赖网络：先替换常见词组（处理「后 / 里 / 发 / 干 / 面」
 * 这类一字多义的坑），再按字表逐字替换。转换结果用于歌词显示和歌词卡，不修改原始歌词。
 */
object HanziScript {

    /** 先处理多义字组成的固定词，避免把「公里」转成「公裡」、把「见面」转成「見麵」。 */
    private val PHRASES: List<Pair<String, String>> = listOf(
        "后来" to "後來", "以后" to "以後", "时候" to "時候", "之后" to "之後",
        "这里" to "這裡", "里面" to "裡面", "心里" to "心裡", "夜里" to "夜裡",
        "头发" to "頭髮", "出发" to "出發", "发现" to "發現", "发生" to "發生",
        "干净" to "乾淨", "干燥" to "乾燥", "干嘛" to "幹嘛", "干活" to "幹活",
        "面条" to "麵條", "见面" to "見面", "表面" to "表面", "前面" to "前面",
        "后台" to "後台", "皇后" to "皇后", "公里" to "公里", "公里数" to "公里數",
        "只有" to "只有", "一只" to "一隻", "只是" to "只是", "一只只" to "一隻隻"
    )

    /** 常用简→繁字表；一字多义的已经尽量留给 PHRASES 处理。 */
    private val SIMPLIFIED_TO_TRADITIONAL: Map<Char, Char> = buildMap {
        fun put(simplified: String, traditional: String) {
            if (simplified.length == traditional.length) {
                simplified.forEachIndexed { i, c -> put(c, traditional[i]) }
            }
        }
        put(
            "爱碍袄坝罢摆败颁办绑帮宝报币毕闭边编变别宾补财参惭苍层产长尝车彻尘陈衬称惩诚迟齿虫础处传疮闯创纯词聪从丛凑窜错达带贷担单胆当党导岛盗灯邓敌递点淀钓调叠钉顶订东动冻独读断队对吨夺鹅额恶儿尔罚阀饭范飞费纷坟奋粪丰风疯冯凤妇该盖赶岗个给龚沟构顾关观馆惯广归龟国过汉号轰红壶护华画怀坏欢环还换黄挥毁汇会秽浑获货祸击鸡积极际继纪夹价驾艰监拣舰姜将浆奖讲酱胶浇骄娇搅矫脚觉较阶节洁结紧尽进经惊竞旧剧据惧开恳垦库夸块亏腊蜡来赖兰拦栏烂涝劳乐垒类泪离礼丽厉励历隶帘联连怜练炼粮两辆疗辽猎临邻岭龄铃灵领刘龙聋笼垄楼芦卢炉陆驴乱仑罗萝络妈马骂吗买卖迈麦满蛮猫门闷们梦弥谜绵庙灭悯鸣谬亩纳难挠脑闹拟酿鸟聂宁拧农齐骑岂启气弃迁签牵钱浅谴枪墙抢桥窍亲轻倾请庆穷区驱权劝却让扰热认荣软锐洒伞丧扫涩杀筛晒伤赏烧绍摄谁审声胜圣师时实识势适释书术树帅双顺说丝松苏诉岁孙台态摊坛叹汤烫涛讨腾题条铁听厅头图团涂脱洼袜弯万网韦违围为伟卫温闻稳务雾牺习系戏细虾辖峡狭厦鲜显险现献县线宪乡详响项销晓啸协挟携谢兴须许悬选学寻训压亚哑烟盐严颜阎厌砚艳验杨养样窑药爷页业叶医亿忆义议艺阴隐应营蝇赢拥佣踊忧优邮犹鱼与屿语预驭郁誉渊园员圆缘远愿跃云郑证帜制质钟终种众昼骤烛筑庄装壮状准资总纵组钻"
            ,
            "愛礙襖壩罷擺敗頒辦綁幫寶報幣畢閉邊編變別賓補財參慚蒼層產長嘗車徹塵陳襯稱懲誠遲齒蟲礎處傳瘡闖創純詞聰從叢湊竄錯達帶貸擔單膽當黨導島盜燈鄧敵遞點澱釣調疊釘頂訂東動凍獨讀斷隊對噸奪鵝額惡兒爾罰閥飯範飛費紛墳奮糞豐風瘋馮鳳婦該蓋趕崗個給龔溝構顧關觀館慣廣歸龜國過漢號轟紅壺護華畫懷壞歡環還換黃揮毀匯會穢渾獲貨禍擊雞積極際繼紀夾價駕艱監揀艦薑將漿獎講醬膠澆驕嬌攪矯腳覺較階節潔結緊盡進經驚競舊劇據懼開懇墾庫誇塊虧臘蠟來賴蘭攔欄爛澇勞樂壘類淚離禮麗厲勵歷隸簾聯連憐練煉糧兩輛療遼獵臨鄰嶺齡鈴靈領劉龍聾籠壟樓蘆盧爐陸驢亂崙羅蘿絡媽馬罵嗎買賣邁麥滿蠻貓門悶們夢彌謎綿廟滅憫鳴謬畝納難撓腦鬧擬釀鳥聶寧擰農齊騎豈啟氣棄遷簽牽錢淺譴槍牆搶橋竅親輕傾請慶窮區驅權勸卻讓擾熱認榮軟銳灑傘喪掃澀殺篩曬傷賞燒紹攝誰審聲勝聖師時實識勢適釋書術樹帥雙順說絲鬆蘇訴歲孫台態攤壇嘆湯燙濤討騰題條鐵聽廳頭圖團塗脫窪襪彎萬網韋違圍為偉衛溫聞穩務霧犧習係戲細蝦轄峽狹廈鮮顯險現獻縣線憲鄉詳響項銷曉嘯協挾攜謝興須許懸選學尋訓壓亞啞煙鹽嚴顏閻厭硯艷驗楊養樣窯藥爺頁業葉醫億憶義議藝陰隱應營蠅贏擁傭踴憂優郵猶魚與嶼語預馭鬱譽淵園員圓緣遠願躍雲鄭證幟製質鐘終種眾晝驟燭築莊裝壯狀準資總縱組鑽"
        )
    }

    /** 字表里漏掉的常用字，单独补一份，避免长串对齐时漏字。 */
    private val EXTRA: Map<Char, Char> = mapOf(
        '话' to '話', '这' to '這', '发' to '發', '干' to '幹',
        '见' to '見', '问' to '問', '间' to '間',
        '么' to '麼', '个' to '個', '还' to '還', '为' to '為', '与' to '與',
        '只' to '隻', '台' to '臺', '体' to '體', '众' to '眾', '准' to '準'
    )

    private val TRADITIONAL_TO_SIMPLIFIED: Map<Char, Char> =
        (SIMPLIFIED_TO_TRADITIONAL + EXTRA).entries.associate { (s, t) -> t to s }

    fun toTraditional(text: String): String =
        convert(text, PHRASES, SIMPLIFIED_TO_TRADITIONAL + EXTRA)

    fun toSimplified(text: String): String {
        val reversedPhrases = PHRASES.map { it.second to it.first }
        return convert(text, reversedPhrases, TRADITIONAL_TO_SIMPLIFIED)
    }

    fun convert(text: String, traditional: Boolean): String =
        if (traditional) toTraditional(text) else text

    private fun convert(
        text: String,
        phrases: List<Pair<String, String>>,
        chars: Map<Char, Char>
    ): String {
        if (text.isEmpty()) return text
        var out = text
        // 长词优先，避免「这时候」先被「时候」拆掉。
        phrases.sortedByDescending { it.first.length }.forEach { (from, to) ->
            if (from.isNotEmpty()) out = out.replace(from, to)
        }
        if (chars.isEmpty()) return out
        return buildString(out.length) {
            out.forEach { c -> append(chars[c] ?: c) }
        }
    }
}
