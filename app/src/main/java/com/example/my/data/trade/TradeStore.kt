package com.example.my.data.trade

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.my.ui.i18n.tr
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/** 模拟盘的资产类别：股票 / 场内基金（ETF、LOF） */
enum class TradeAsset(val zh: String, val en: String) {
    STOCK("股票", "Stock"),
    FUND("基金", "Fund");

    companion object {
        /** 6 位代码里 5 开头（沪市）与 15/16 开头（深市）都按场内基金处理 */
        fun ofCode(code: String): TradeAsset {
            val c = code.trim().lowercase()
            return when {
                c.startsWith("sh5") || c.startsWith("sz15") || c.startsWith("sz16") -> FUND
                else -> STOCK
            }
        }
    }
}

enum class TradeSide(val zh: String, val en: String) {
    BUY("买入", "Buy"),
    SELL("卖出", "Sell")
}

enum class OrderType(val zh: String, val en: String) {
    LIMIT("限价", "Limit"),
    MARKET("市价", "Market")
}

enum class OrderStatus(val zh: String, val en: String) {
    PENDING("挂单中", "Pending"),
    FILLED("已成交", "Filled"),
    CANCELLED("已撤单", "Cancelled"),
    REJECTED("已拒绝", "Rejected")
}

/** 一条持仓：数量与持仓均价（买入费用摊进均价，卖出按均价扣减） */
data class TradePosition(
    val code: String,
    val name: String,
    val qty: Int,
    val avgCost: Double,
    val asset: TradeAsset = TradeAsset.ofCode(code)
)

/** 一条委托 / 成交记录 */
data class TradeOrder(
    val id: Long,
    val code: String,
    val name: String,
    val side: TradeSide,
    val type: OrderType,
    /** 限价单的委托价；市价单记 0 */
    val limitPrice: Double,
    val qty: Int,
    val status: OrderStatus,
    /** 成交价，未成交为 0 */
    val fillPrice: Double = 0.0,
    /** 成交数量，未成交为 0 */
    val fillQty: Int = 0,
    val fee: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val filledAt: Long = 0L,
    val asset: TradeAsset = TradeAsset.ofCode(code)
) {
    val filled: Boolean get() = status == OrderStatus.FILLED
}

/** 资金流水 */
data class CashFlow(
    val id: Long,
    val at: Long,
    val label: String,
    val amount: Double
)

/** 自选里的一条 */
data class WatchItem(val code: String, val name: String)

/** 定投计划：App 打开且到期时按市价买入 amount 元 */
data class InvestPlan(
    val id: Long,
    val code: String,
    val name: String,
    val amount: Double,
    val everyDays: Int,
    val lastAt: Long = 0L
)

/** 风险测评结果 */
enum class RiskLevel(val zh: String, val en: String) {
    CONSERVATIVE("保守型", "Conservative"),
    STEADY("稳健型", "Balanced"),
    AGGRESSIVE("积极型", "Aggressive")
}

/**
 * 本地模拟交易账户。
 *
 * 行情来自 [com.example.my.data.stock.StockApi] 的公开接口，**撮合完全在本机**：
 * - 资金、持仓、委托、流水全部落在 SharedPreferences（键 trade_account），杀进程不丢；
 * - 不会向任何券商下单，页面里始终标注「模拟盘」；
 * - 费用按 A 股通行口径粗算：佣金 0.025%（最低 5 元），股票卖出加 0.1% 印花税；
 * - 挂单不冻结资金，成交那一刻才校验可用资金，避免「挂单占着钱不能买别的」这种困惑。
 */
object TradeStore {

    private const val PREFS = "trade_account"
    private const val KEY_STATE = "state"
    private const val INITIAL_CASH = 100_000.0
    private const val MIN_COMMISSION = 5.0
    private const val COMMISSION_RATE = 0.00025
    private const val STAMP_RATE = 0.001

    var cash by mutableStateOf(INITIAL_CASH)
        private set
    var positions by mutableStateOf<List<TradePosition>>(emptyList())
        private set
    var orders by mutableStateOf<List<TradeOrder>>(emptyList())
        private set
    var flows by mutableStateOf<List<CashFlow>>(emptyList())
        private set
    var watchlist by mutableStateOf<List<WatchItem>>(emptyList())
        private set
    var riskLevel by mutableStateOf<RiskLevel?>(null)
        private set
    var riskScore by mutableStateOf(0)
        private set
    var plans by mutableStateOf<List<InvestPlan>>(emptyList())
        private set

    private var contextRef: Context? = null
    private var loaded = false
    private var nextId = 1L

    /** 已实现盈亏：卖出时按（成交价 - 持仓均价）累计，用来和浮动盈亏分开看 */
    var realizedPnl by mutableStateOf(0.0)
        private set

    /** 累计净转入（转入为正、转出为负）：算总收益率时要扣掉它，否则一转账收益率就失真 */
    var netTransfer by mutableStateOf(0.0)
        private set

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        contextRef = context.applicationContext
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STATE, null)
        if (raw.isNullOrBlank()) {
            resetTo(INITIAL_CASH, silent = true)
            return
        }
        runCatching {
            val o = JSONObject(raw)
            cash = o.optDouble("cash", INITIAL_CASH)
            realizedPnl = o.optDouble("realized", 0.0)
            netTransfer = o.optDouble("netTransfer", 0.0)
            nextId = o.optLong("nextId", 1L)
            positions = o.optJSONArray("positions").orEmpty().mapObjects { j ->
                TradePosition(
                    code = j.optString("code"),
                    name = j.optString("name"),
                    qty = j.optInt("qty"),
                    avgCost = j.optDouble("avgCost", 0.0),
                    asset = runCatching { TradeAsset.valueOf(j.optString("asset", "STOCK")) }
                        .getOrDefault(TradeAsset.STOCK)
                )
            }
            orders = o.optJSONArray("orders").orEmpty().mapObjects { j ->
                TradeOrder(
                    id = j.optLong("id"),
                    code = j.optString("code"),
                    name = j.optString("name"),
                    side = runCatching { TradeSide.valueOf(j.optString("side", "BUY")) }
                        .getOrDefault(TradeSide.BUY),
                    type = runCatching { OrderType.valueOf(j.optString("type", "LIMIT")) }
                        .getOrDefault(OrderType.LIMIT),
                    limitPrice = j.optDouble("limitPrice", 0.0),
                    qty = j.optInt("qty"),
                    status = runCatching { OrderStatus.valueOf(j.optString("status", "PENDING")) }
                        .getOrDefault(OrderStatus.PENDING),
                    fillPrice = j.optDouble("fillPrice", 0.0),
                    fillQty = j.optInt("fillQty", 0),
                    fee = j.optDouble("fee", 0.0),
                    createdAt = j.optLong("createdAt"),
                    filledAt = j.optLong("filledAt", 0L),
                    asset = runCatching { TradeAsset.valueOf(j.optString("asset", "STOCK")) }
                        .getOrDefault(TradeAsset.STOCK)
                )
            }
            flows = o.optJSONArray("flows").orEmpty().mapObjects { j ->
                CashFlow(
                    id = j.optLong("id"),
                    at = j.optLong("at"),
                    label = j.optString("label"),
                    amount = j.optDouble("amount", 0.0)
                )
            }
            watchlist = o.optJSONArray("watch").orEmpty().mapObjects { j ->
                WatchItem(j.optString("code"), j.optString("name"))
            }
            riskScore = o.optInt("riskScore", 0)
            riskLevel = o.optString("riskLevel")
                .takeIf { it.isNotBlank() }
                ?.let { id -> runCatching { RiskLevel.valueOf(id) }.getOrNull() }
            plans = o.optJSONArray("plans").orEmpty().mapObjects { j ->
                InvestPlan(
                    id = j.optLong("id"),
                    code = j.optString("code"),
                    name = j.optString("name"),
                    amount = j.optDouble("amount", 0.0),
                    everyDays = j.optInt("everyDays", 30),
                    lastAt = j.optLong("lastAt", 0L)
                )
            }
        }.onFailure {
            // 存档坏了就按新账户重来，不让用户卡在损坏的 JSON 里
            resetTo(INITIAL_CASH, silent = true)
        }
    }

    /** 重置模拟账户（清空持仓、委托、流水），资金回到 10 万，风险测评保留 */
    fun resetSimulation(context: Context) {
        resetTo(INITIAL_CASH, silent = false)
        persist()
    }

    private fun resetTo(initial: Double, silent: Boolean) {
        cash = initial
        positions = emptyList()
        orders = emptyList()
        flows = listOf(
            CashFlow(
                id = nextId++,
                at = System.currentTimeMillis(),
                label = tr("模拟账户初始资金", "Initial virtual cash"),
                amount = initial
            )
        )
        realizedPnl = 0.0
        netTransfer = 0.0
        if (!silent) watchlist = watchlist
    }

    // ---------------- 自选 ----------------

    fun toggleWatch(code: String, name: String) {
        watchlist = if (watchlist.any { it.code == code }) {
            watchlist.filterNot { it.code == code }
        } else {
            watchlist + WatchItem(code, name)
        }
        persist()
    }

    fun isWatched(code: String): Boolean = watchlist.any { it.code == code }

    // ---------------- 下单 / 撤单 ----------------

    /**
     * 下单。quote 必须是当前真实行情（市价单按它成交，限价单按它判断能否立刻成交）。
     * 返回的字符串是给用户看的结果说明，失败时不会改动任何状态。
     */
    fun place(
        side: TradeSide,
        type: OrderType,
        limitPrice: Double,
        qty: Int,
        quote: QuoteLite
    ): String {
        if (qty <= 0) return tr("数量要大于 0", "Quantity must be positive")
        val asset = TradeAsset.ofCode(quote.code)
        if (asset == TradeAsset.STOCK && qty % 100 != 0) {
            return tr("股票按 100 股整手下单，当前 $qty 股", "Stocks trade in lots of 100")
        }
        if (quote.price <= 0.0) return tr("当前没有有效行情，稍后再试", "No valid quote yet")
        if (type == OrderType.LIMIT && limitPrice <= 0.0) {
            return tr("限价单要填委托价", "Limit price required")
        }
        val price = if (type == OrderType.MARKET) quote.price else limitPrice
        val immediate = type == OrderType.MARKET ||
            (side == TradeSide.BUY && limitPrice >= quote.price) ||
            (side == TradeSide.SELL && limitPrice <= quote.price)

        // 卖出：先校验可卖数量（挂单中的卖出不占额度，这里按现有持仓校验）
        if (side == TradeSide.SELL) {
            val held = positionOf(quote.code)?.qty ?: 0
            if (qty > held) return tr("可卖 $held 股，不够", "Only $held available")
        }
        if (side == TradeSide.BUY && immediate) {
            val need = price * qty + feeOf(price * qty, asset, TradeSide.BUY)
            if (need > cash + 0.005) {
                return tr("可用资金不足，需要 ${money(need)}", "Insufficient cash，need ${money(need)}")
            }
        }

        val order = TradeOrder(
            id = nextId++,
            code = quote.code,
            name = quote.name,
            side = side,
            type = type,
            limitPrice = if (type == OrderType.LIMIT) limitPrice else 0.0,
            qty = qty,
            status = if (immediate) OrderStatus.FILLED else OrderStatus.PENDING,
            asset = asset
        )
        orders = listOf(order) + orders
        if (immediate) {
            val result = fill(order, quote.price)
            if (result != null) {
                // 成交失败（例如挂单后资金被别的单子用掉）要把这条改成拒绝
                orders = orders.map { if (it.id == order.id) it.copy(status = OrderStatus.REJECTED) else it }
                persist()
                return result
            }
            persist()
            return tr(
                "已成交 · ${quote.name} ${side.zh} $qty 股 @ ${fmt(quote.price)}",
                "Filled · ${quote.name} ${side.en} $qty @ ${fmt(quote.price)}"
            )
        }
        persist()
        return tr("已挂单 · 等价格到 ${fmt(limitPrice)} 自动成交", "Order placed · waits for ${fmt(limitPrice)}")
    }

    /**
     * 用最新行情推进挂单：买入限价 >= 现价、卖出限价 <= 现价时按现价成交。
     * 返回本次成交了几笔，调用方据此刷新列表并提示。
     */
    fun settlePending(quotes: Map<String, QuoteLite>): Int {
        val pending = orders.filter { it.status == OrderStatus.PENDING }
        if (pending.isEmpty()) return 0
        var filled = 0
        for (o in pending) {
            val q = quotes[o.code] ?: continue
            if (q.price <= 0.0) continue
            val crossed = when (o.side) {
                TradeSide.BUY -> o.limitPrice >= q.price
                TradeSide.SELL -> o.limitPrice <= q.price
            }
            if (!crossed) continue
            val err = fill(o, q.price)
            orders = orders.map {
                when {
                    it.id != o.id -> it
                    err == null -> it.copy(status = OrderStatus.FILLED)
                    else -> it.copy(status = OrderStatus.REJECTED)
                }
            }
            if (err == null) filled++
        }
        if (filled > 0) persist()
        return filled
    }

    /** 撤单：只有挂单中的能撤 */
    fun cancel(orderId: Long): Boolean {
        val o = orders.firstOrNull { it.id == orderId } ?: return false
        if (o.status != OrderStatus.PENDING) return false
        orders = orders.map { if (it.id == orderId) it.copy(status = OrderStatus.CANCELLED) else it }
        persist()
        return true
    }

    /** 成交结算。返回 null 表示成功，否则是失败原因（这条委托会被标记为拒绝） */
    private fun fill(order: TradeOrder, price: Double): String? {
        val amount = price * order.qty
        val fee = feeOf(amount, order.asset, order.side)
        return if (order.side == TradeSide.BUY) {
            val total = amount + fee
            if (total > cash + 0.005) {
                tr("资金不足，成交失败", "Not enough cash to fill")
            } else {
                cash -= total
                val old = positionOf(order.code)
                val newQty = (old?.qty ?: 0) + order.qty
                val newCost = ((old?.qty ?: 0) * (old?.avgCost ?: 0.0) + total) / newQty
                val pos = TradePosition(order.code, order.name, newQty, newCost, order.asset)
                positions = positions.filterNot { it.code == order.code } + pos
                orders = orders.map {
                    if (it.id == order.id) it.copy(
                        status = OrderStatus.FILLED,
                        fillPrice = price,
                        fillQty = order.qty,
                        fee = fee,
                        filledAt = System.currentTimeMillis()
                    ) else it
                }
                flows = listOf(
                    CashFlow(nextId++, System.currentTimeMillis(), tr("买入 ${order.name}", "Buy ${order.name}"), -total)
                ) + flows
                null
            }
        } else {
            val held = positionOf(order.code)
            if (held == null || held.qty < order.qty) {
                tr("持仓不足，成交失败", "Not enough holdings to fill")
            } else {
                val net = amount - fee
                cash += net
                realizedPnl += (price - held.avgCost) * order.qty - fee
                val left = held.qty - order.qty
                positions = if (left <= 0) {
                    positions.filterNot { it.code == order.code }
                } else {
                    positions.map { if (it.code == order.code) it.copy(qty = left) else it }
                }
                orders = orders.map {
                    if (it.id == order.id) it.copy(
                        status = OrderStatus.FILLED,
                        fillPrice = price,
                        fillQty = order.qty,
                        fee = fee,
                        filledAt = System.currentTimeMillis()
                    ) else it
                }
                flows = listOf(
                    CashFlow(nextId++, System.currentTimeMillis(), tr("卖出 ${order.name}", "Sell ${order.name}"), net)
                ) + flows
                null
            }
        }
    }

    private fun feeOf(amount: Double, asset: TradeAsset, side: TradeSide): Double {
        // 场内基金多数免佣或极低，这里按 0.01% 估；股票按 0.025% 且最低 5 元
        val commission = if (asset == TradeAsset.FUND) {
            max(amount * 0.0001, 0.0)
        } else {
            max(amount * COMMISSION_RATE, MIN_COMMISSION)
        }
        val stamp = if (side == TradeSide.SELL && asset == TradeAsset.STOCK) amount * STAMP_RATE else 0.0
        return commission + stamp
    }

    fun positionOf(code: String): TradePosition? = positions.firstOrNull { it.code == code }

    /** 某只标的的可用数量（挂单不占用，直接取持仓） */
    fun available(code: String): Int = positionOf(code)?.qty ?: 0

    /**
     * 按可用资金估算能买多少股（含费用，留 1% 余量避免手续费把最后一点钱花超）。
     * 股票向下取整到 100 股。
     */
    fun maxBuyQty(price: Double, asset: TradeAsset): Int {
        if (price <= 0.0) return 0
        val budget = cash * 0.99
        var qty = (budget / price).toInt()
        if (asset == TradeAsset.STOCK) qty = (qty / 100) * 100
        if (qty <= 0) return 0
        // 扣掉费用后再退一档，保证真的能成交
        while (qty > 0 && price * qty + feeOf(price * qty, asset, TradeSide.BUY) > cash) {
            qty -= if (asset == TradeAsset.STOCK) 100 else 1
        }
        return qty
    }

    // ---------------- 风险测评 ----------------

    fun saveRisk(score: Int) {
        riskScore = score
        riskLevel = when {
            score <= 10 -> RiskLevel.CONSERVATIVE
            score <= 15 -> RiskLevel.STEADY
            else -> RiskLevel.AGGRESSIVE
        }
        persist()
    }

    // ---------------- 银证转账 · 定投 ----------------

    /** 模拟转入/转出资金；转出不能超过可用资金。返回实际变动金额（正=转入） */
    fun transferCash(amount: Double, deposit: Boolean): Double {
        if (amount <= 0) return 0.0
        val real = if (deposit) amount else min(amount, cash)
        if (real <= 0.0) return 0.0
        cash = if (deposit) cash + real else cash - real
        netTransfer += if (deposit) real else -real
        flows = listOf(
            CashFlow(
                nextId++,
                System.currentTimeMillis(),
                tr(
                    if (deposit) "银证转入（模拟）" else "银证转出（模拟）",
                    if (deposit) "Transfer in (simulated)" else "Transfer out (simulated)"
                ),
                if (deposit) real else -real
            )
        ) + flows
        persist()
        return if (deposit) real else -real
    }

    fun addPlan(code: String, name: String, amount: Double, everyDays: Int) {
        if (amount <= 0 || everyDays <= 0) return
        plans = plans + InvestPlan(
            id = nextId++,
            code = code,
            name = name,
            amount = amount,
            everyDays = everyDays,
            // 从今天算起，满一个周期才第一次执行
            lastAt = System.currentTimeMillis()
        )
        persist()
    }

    fun removePlan(id: Long) {
        plans = plans.filterNot { it.id == id }
        persist()
    }

    /**
     * 到期定投：App 打开并拿到行情时执行一次。
     * 金额换股数，股票按整手取整；买不起一手就跳过并在返回里说明。
     */
    fun runDuePlans(quotes: Map<String, QuoteLite>): Int {
        if (plans.isEmpty()) return 0
        val now = System.currentTimeMillis()
        var done = 0
        var updated = plans
        plans.forEach { p ->
            if (now - p.lastAt < p.everyDays.toLong() * 86_400_000L) return@forEach
            val q = quotes[p.code] ?: return@forEach
            if (q.price <= 0.0) return@forEach
            var qty = (p.amount / q.price).toInt()
            if (TradeAsset.ofCode(q.code) == TradeAsset.STOCK) qty = (qty / 100) * 100
            if (qty <= 0) return@forEach
            val before = orders.size
            place(TradeSide.BUY, OrderType.MARKET, 0.0, qty, q)
            val ok = orders.size > before &&
                orders.firstOrNull()?.status == OrderStatus.FILLED
            if (ok) {
                updated = updated.map { if (it.id == p.id) it.copy(lastAt = now) else it }
                done++
            }
        }
        if (updated != plans) {
            plans = updated
            persist()
        }
        return done
    }

    // ---------------- 持久化 ----------------

    fun persist() {
        val c = contextRef ?: return
        val o = JSONObject()
            .put("cash", cash)
            .put("realized", realizedPnl)
            .put("netTransfer", netTransfer)
            .put("nextId", nextId)
            .put("riskScore", riskScore)
            .put("riskLevel", riskLevel?.name.orEmpty())
        o.put("positions", JSONArray().also { arr ->
            positions.forEach { p ->
                arr.put(
                    JSONObject()
                        .put("code", p.code)
                        .put("name", p.name)
                        .put("qty", p.qty)
                        .put("avgCost", p.avgCost)
                        .put("asset", p.asset.name)
                )
            }
        })
        o.put("orders", JSONArray().also { arr ->
            orders.take(200).forEach { t ->
                arr.put(
                    JSONObject()
                        .put("id", t.id)
                        .put("code", t.code)
                        .put("name", t.name)
                        .put("side", t.side.name)
                        .put("type", t.type.name)
                        .put("limitPrice", t.limitPrice)
                        .put("qty", t.qty)
                        .put("status", t.status.name)
                        .put("fillPrice", t.fillPrice)
                        .put("fillQty", t.fillQty)
                        .put("fee", t.fee)
                        .put("createdAt", t.createdAt)
                        .put("filledAt", t.filledAt)
                        .put("asset", t.asset.name)
                )
            }
        })
        o.put("flows", JSONArray().also { arr ->
            flows.take(200).forEach { f ->
                arr.put(
                    JSONObject()
                        .put("id", f.id)
                        .put("at", f.at)
                        .put("label", f.label)
                        .put("amount", f.amount)
                )
            }
        })
        o.put("watch", JSONArray().also { arr ->
            watchlist.forEach { w ->
                arr.put(JSONObject().put("code", w.code).put("name", w.name))
            }
        })
        o.put("plans", JSONArray().also { arr ->
            plans.forEach { p ->
                arr.put(
                    JSONObject()
                        .put("id", p.id)
                        .put("code", p.code)
                        .put("name", p.name)
                        .put("amount", p.amount)
                        .put("everyDays", p.everyDays)
                        .put("lastAt", p.lastAt)
                )
            }
        })
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATE, o.toString())
            .apply()
    }

    // ---------------- 计算 ----------------

    /** 总市值（按传入行情；缺行情的按成本价估算，不把它当 0 让资产突然缩水） */
    fun marketValue(quotes: Map<String, QuoteLite>): Double =
        positions.sumOf { p -> p.qty * (quotes[p.code]?.price?.takeIf { it > 0 } ?: p.avgCost) }

    fun totalAssets(quotes: Map<String, QuoteLite>): Double = cash + marketValue(quotes)

    /** 当日盈亏：持仓（现价 - 昨收）× 数量 */
    fun dayPnl(quotes: Map<String, QuoteLite>): Double =
        positions.sumOf { p ->
            val q = quotes[p.code] ?: return@sumOf 0.0
            if (q.prevClose <= 0.0 || q.price <= 0.0) 0.0 else (q.price - q.prevClose) * p.qty
        }

    /** 浮动盈亏（相对持仓均价） */
    fun floatPnl(quotes: Map<String, QuoteLite>): Double =
        positions.sumOf { p ->
            val price = quotes[p.code]?.price?.takeIf { it > 0 } ?: p.avgCost
            (price - p.avgCost) * p.qty
        }

    fun totalReturn(quotes: Map<String, QuoteLite>): Double {
        // 基准 = 初始 10 万 + 净转入：转账不该被算成收益
        val base = INITIAL_CASH + netTransfer
        return if (base <= 0) 0.0 else (totalAssets(quotes) - base) / base
    }
}

/** 交易 / 撮合只需要这几个字段 */
data class QuoteLite(
    val code: String,
    val name: String,
    val price: Double,
    val prevClose: Double,
    val changePct: Double,
    val turnover: Double = 0.0,
    val asset: TradeAsset = TradeAsset.ofCode(code)
)

private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

private inline fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> {
    val out = mutableListOf<T>()
    for (i in 0 until length()) {
        val j = optJSONObject(i) ?: continue
        runCatching { out += block(j) }
    }
    return out
}

private fun fmt(v: Double): String = String.format(java.util.Locale.US, "%.2f", v)
private fun money(v: Double): String = String.format(java.util.Locale.US, "%.2f 元", v)
