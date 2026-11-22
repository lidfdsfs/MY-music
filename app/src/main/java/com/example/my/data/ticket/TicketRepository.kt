package com.example.my.data.ticket

/**
 * 短时内存缓存：同一出发地/到达地/日期在 5 分钟内不重复打第三方接口。
 *
 * 目的不是提速，而是**减少请求次数**：每多打一次，对方就多拿到一次
 * 「谁在什么时候查了哪条线路」。缓存只存在进程内，退出 App 即消失，
 * 不写数据库、不写文件。
 */
object TicketRepository {

    private const val TTL_MS = 5 * 60 * 1000L
    private const val MAX_ENTRIES = 8
    private val lock = Any()
    private val cache = LinkedHashMap<String, Pair<Long, Any>>()

    suspend fun trains(from: String, to: String, date: String): TrainQueryResult {
        val key = "T|${from.trim()}|${to.trim()}|$date"
        cached(key)?.let { return it as TrainQueryResult }
        val fresh = TrainTicketApi.query(from, to, date)
        remember(key, fresh)
        return fresh
    }

    suspend fun flights(from: String, to: String, date: String): FlightQueryResult {
        val key = "F|${from.trim()}|${to.trim()}|$date"
        cached(key)?.let { return it as FlightQueryResult }
        val fresh = FlightTicketApi.search(from, to, date)
        remember(key, fresh)
        return fresh
    }

    private fun cached(key: String): Any? = synchronized(lock) {
        val hit = cache[key] ?: return@synchronized null
        if (System.currentTimeMillis() - hit.first > TTL_MS) {
            cache.remove(key)
            null
        } else {
            hit.second
        }
    }

    private fun remember(key: String, value: Any) = synchronized(lock) {
        cache[key] = System.currentTimeMillis() to value
        while (cache.size > MAX_ENTRIES) {
            val oldest = cache.keys.firstOrNull() ?: break
            cache.remove(oldest)
        }
    }
}
