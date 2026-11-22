package com.example.my.data.map

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.util.BoundingBox
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * 离线瓦片下载器：直接用 osmdroid 自带的 [CacheManager] 把矩形范围内的瓦片抓下来，
 * 写进地图平时在用的那套瓦片缓存（sqlite），所以下载完就能离线看图，不需要第二套读取逻辑。
 *
 * 关键点：
 * - CacheManager 保存瓦片时 expiry 为 null，即「永不判过期」，不会被 osmdroid 的过期清理扫掉；
 * - 删除某个县用的是同一范围的瓦片逐张 delete，只删这块，不动别人；
 * - 字节数按缓存库文件前后增量算，不估算——用户看到的体积就是实际占的。
 */
object OfflineTiles {

    /** 离线包最低到 6 级：再往外看就是全国轮廓，没必要为每个县重复下载 */
    const val MIN_ZOOM = 6

    /** 标准：6-12 级，看清乡镇与主干路 */
    const val MAX_ZOOM_STANDARD = 12

    /** 精细：6-14 级，能看清街道 */
    const val MAX_ZOOM_DETAIL = 14

    private const val SUPPORTED_MAX_ZOOM = 19

    /** 一张瓦片的经验体积，仅用于下载前的「预计大小」，真实占用以下载结果为准 */
    private const val TILE_BYTES_HINT = 9_000L

    data class Result(val tiles: Int, val bytes: Long, val errors: Int)

    /** 让 osmdroid 的缓存落到私有 files 目录：cacheDir 会被系统在空间紧张时整目录清掉，离线包不能放那儿 */
    fun prepare(context: Context) {
        Configuration.getInstance().apply {
            userAgentValue = "MYFootprint/1.0 (Android)"
            val root = File(context.filesDir, "osmdroid").apply { runCatching { mkdirs() } }
            osmdroidBasePath = root
            osmdroidTileCache = File(root, "tiles")
            // 默认上限只有 600MB：按县下几个省就会顶到上限，而 osmdroid 的按体积裁剪不区分
            // 「随手浏览的缓存」和「用户特意下的离线包」，会把整片离线瓦片当旧数据删掉。
            // 这里把上限抬到 4GB / 3GB 触发线，留出足够空间，同时仍然有硬上限防止无限增长。
            tileFileSystemCacheMaxBytes = 4L * 1024 * 1024 * 1024
            tileFileSystemCacheTrimBytes = 3L * 1024 * 1024 * 1024
        }
    }

    /** 该矩形在给定层级下有多少张瓦片（各层相加），用于下载前提示 */
    fun estimate(bb: BoundingBox, minZoom: Int, maxZoom: Int): Int {
        var total = 0
        for (z in minZoom..maxZoom) {
            total += runCatching { CacheManager.getTilesCoverage(bb, z).size }.getOrDefault(0)
        }
        return total
    }

    fun estimateBytes(tiles: Int): Long = tiles.toLong() * TILE_BYTES_HINT

    /**
     * 下载一块区域的瓦片。回调里的 (已下, 总数) 用来画进度；返回实际下了多少张、占了多少字节。
     * 中途取消走协程取消，内部会把 CacheManager 的任务一并停掉。
     */
    suspend fun download(
        context: Context,
        source: ITileSource,
        bb: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Result = withContext(Dispatchers.IO) {
        prepare(context)
        val before = cacheDbSize()
        val total = AtomicInteger(0)
        val done = AtomicInteger(0)
        val manager = CacheManager(source, SqlTileWriter(), MIN_ZOOM, SUPPORTED_MAX_ZOOM)

        suspendCancellableCoroutine { cont ->
            val callback = object : CacheManager.CacheManagerCallback {
                override fun downloadStarted() = Unit

                override fun setPossibleTilesInArea(totalTiles: Int) {
                    total.set(totalTiles)
                    onProgress(0, totalTiles)
                }

                override fun updateProgress(
                    progress: Int,
                    currentZoomLevel: Int,
                    zoomMin: Int,
                    zoomMax: Int
                ) {
                    done.set(progress)
                    val all = if (total.get() > 0) total.get() else progress
                    onProgress(progress, all)
                }

                override fun onTaskComplete() {
                    if (!cont.isActive) return
                    val delta = (cacheDbSize() - before).coerceAtLeast(0L)
                    val count = maxOf(done.get(), total.get())
                    cont.resume(Result(count, delta, 0))
                }

                override fun onTaskFailed(errors: Int) {
                    if (!cont.isActive) return
                    val delta = (cacheDbSize() - before).coerceAtLeast(0L)
                    cont.resume(Result(done.get(), delta, maxOf(errors, 1)))
                }
            }
            val launched = runCatching {
                manager.downloadAreaAsyncNoUI(context, bb, minZoom, maxZoom, callback)
            }
            if (launched.isFailure && cont.isActive) {
                cont.resume(Result(0, 0, 1))
            }
            cont.invokeOnCancellation {
                runCatching { manager.cancelAllJobs() }
            }
        }
    }

    /** 只删这块区域内的瓦片，返回删掉的张数 */
    suspend fun delete(
        context: Context,
        source: ITileSource,
        bb: BoundingBox,
        minZoom: Int,
        maxZoom: Int
    ): Int = withContext(Dispatchers.IO) {
        prepare(context)
        val manager = runCatching {
            CacheManager(source, SqlTileWriter(), MIN_ZOOM, SUPPORTED_MAX_ZOOM)
        }.getOrNull() ?: return@withContext 0
        var removed = 0
        for (z in minZoom..maxZoom) {
            val tiles = runCatching { CacheManager.getTilesCoverage(bb, z) }.getOrDefault(emptyList())
            tiles.forEach { index ->
                if (runCatching { manager.deleteTile(index) }.getOrDefault(false)) removed++
            }
        }
        removed
    }

    /** 清空整套瓦片缓存（含平时随手看地图留下的），用于真正腾空间 */
    fun purgeAll(): Boolean = runCatching { SqlTileWriter().purgeCache() }.getOrDefault(false)

    /** 当前瓦片缓存库占用的字节数（含 WAL） */
    fun cacheDbSize(): Long {
        val dir = Configuration.getInstance().osmdroidTileCache ?: return 0L
        val base = File(dir, SqlTileWriter.DATABASE_FILENAME)
        val wal = File(dir, SqlTileWriter.DATABASE_FILENAME + "-wal")
        return base.length().coerceAtLeast(0L) + wal.length().coerceAtLeast(0L)
    }
}
