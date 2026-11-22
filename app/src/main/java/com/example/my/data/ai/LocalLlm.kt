package com.example.my.data.ai

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/** 可下载的本地模型条目（ModelScope 上 MNN 官方仓库的量化包，文件平铺、含 config.json） */
data class LocalModelSpec(
    /** 目录名，与仓库名保持一致，下载后直接落到 filesDir/models/<id>/ */
    val id: String,
    val display: String,
    val sizeHint: String,
    /** 粗估下载体积（MB），用于存储空间判断与推荐，不必精确 */
    val approxMb: Long,
    val desc: String,
    /** ModelScope 仓库：org/name */
    val repo: String
)

/**
 * 本地大模型（MNN + 小参数量化模型）管理。
 *
 * 引擎：MNN 3.6.2 官方预编译的 .so（libllm.so / libMNN*.so）+ 自写的薄 JNI 封装
 * libmnnllmjni.so，全部随 APK 打包（两个 ABI 共约 12MB）。
 *
 * 模型：设置页内直接从 ModelScope 下载到 filesDir/models/<id>/（840MB 级，
 * 不进 APK —— 打进安装包会让 APK 变成 1GB 级）。下载完成即自动设为当前模型，
 * 也保留「导入已解压文件夹」的手工通道。多模型并存，可随时切换或单个删除。
 */
object LocalLlm {

    /** 单次回复的最大 token 数：1.5B 在手机上生成偏慢，先限制在能答完一句话的量 */
    private const val MAX_NEW_TOKENS = 512

    private const val PREFS = "local_llm"
    private const val KEY_ACTIVE = "active_model"

    /** 模型商店：都是 MNN 官方导出的 int8/int4 小模型，手机上跑得动 */
    val CATALOG = listOf(
        LocalModelSpec(
            id = "Qwen2.5-1.5B-Instruct-MNN",
            display = "Qwen2.5-1.5B",
            sizeHint = "约 840 MB · int8",
            approxMb = 840L,
            desc = "通义千问小杯，通用对话最稳，推荐",
            repo = "MNN/Qwen2.5-1.5B-Instruct-MNN"
        ),
        LocalModelSpec(
            id = "Qwen3-4B-Instruct-2507-MNN",
            display = "Qwen3-4B",
            sizeHint = "约 2.6 GB · int8",
            approxMb = 2600L,
            desc = "新一代 Qwen3，回答更准更长，需预留存储空间",
            repo = "MNN/Qwen3-4B-Instruct-2507-MNN"
        ),
        LocalModelSpec(
            id = "Qwen3-4B-Thinking-2507-MNN",
            display = "Qwen3-4B 思考版",
            sizeHint = "约 2.6 GB · int8",
            approxMb = 2600L,
            desc = "Qwen3 深度思考版，复杂问题先推理再作答",
            repo = "MNN/Qwen3-4B-Thinking-2507-MNN"
        ),
        LocalModelSpec(
            id = "DeepSeek-R1-Distill-Qwen-1.5B-MNN",
            display = "DeepSeek-R1-1.5B",
            sizeHint = "约 1.0 GB · int8",
            approxMb = 1000L,
            desc = "DeepSeek 蒸馏版，推理能力更强",
            repo = "MNN/DeepSeek-R1-1.5B-Qwen-MNN"
        ),
        LocalModelSpec(
            id = "Qwen3-1.7B-MNN",
            display = "Qwen3-1.7B",
            sizeHint = "约 1.2 GB · int8",
            approxMb = 1200L,
            desc = "Qwen3 均衡款，速度与质量兼顾",
            repo = "MNN/Qwen3-1.7B-MNN"
        ),
        LocalModelSpec(
            id = "Qwen3-0.6B-MNN",
            display = "Qwen3-0.6B",
            sizeHint = "约 430 MB · int8",
            approxMb = 430L,
            desc = "Qwen3 小杯，秒回，适合轻问答",
            repo = "MNN/Qwen3-0.6B-MNN"
        ),
        LocalModelSpec(
            id = "Qwen2.5-0.5B-Instruct-MNN",
            display = "Qwen2.5-0.5B",
            sizeHint = "约 350 MB · int8",
            approxMb = 350L,
            desc = "最小最快，低配机也能跑，回答较简短",
            repo = "MNN/Qwen2.5-0.5B-Instruct-MNN"
        ),
        LocalModelSpec(
            id = "MNN-Pocket-0.3B-MNN",
            display = "Pocket-0.3B",
            sizeHint = "约 166 MB · int4",
            approxMb = 166L,
            desc = "超轻量备用包，无网应急也能聊几句",
            repo = "MNN/MNN-Pocket-0.3B-MNN"
        )
    )

    /** 引擎会话句柄（0 表示还没建）；MNN 的 Llm 对象很重，只建一次复用 */
    private val sessionLock = Any()
    private var session = 0L
    /** 当前会话加载的是哪个本地模型；按功能选不同模型时要换载 */
    private var sessionModelId: String? = null
    /** 当前会话用的运行配置（模型 + 上下文 tokens）；改了上下文要重建会话 */
    private var sessionConfigKey: String? = null

    // ---------- 下载状态（应用级协程，离开设置页也不断线下载） ----------

    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadJobs = mutableMapOf<String, Job>()

    /** id -> 0..100；Compose 可观察。键消失 = 空闲/已结束 */
    var downloadProgress by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    /** id -> 最近一次失败原因（下次开始下载时清除） */
    var downloadError by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    // ---------- 目录与状态 ----------

    /** 模型根目录（所有本地模型都放这里） */
    fun modelRoot(context: Context): File = File(context.filesDir, "models")

    /** 指定模型目录 */
    fun modelDir(context: Context, id: String): File = File(modelRoot(context), id)

    /** 引擎是否可用：JNI 封装与 libllm.so 是否成功加载 */
    fun engineAvailable(): Boolean = MnnLlmBridge.available

    /**
     * 某模型是否已就位。
     * 关键：不能只看 config.json——它只是个几 KB 的入口，大权重才是模型本体。
     * 下载中途断网时 config.json 先落盘、权重没下完，若只认 config.json 就会"假下载"：
     * 界面显示已下载，实际 MNN 加载必崩。这里必须校验权重文件真实存在且达到 MB 级。
     */
    fun modelDownloaded(context: Context, id: String): Boolean {
        val dir = modelDir(context, id)
        if (!dir.isDirectory) return false
        val cfg = File(dir, "config.json")
        if (!cfg.isFile || cfg.length() < 10) return false
        return runCatching {
            val o = JSONObject(cfg.readText())
            val modelFile = o.optString("llm_model", "llm.mnn")
            val weightFile = o.optString("llm_weight", "llm.mnn.weight")
            val m = File(dir, modelFile)
            val w = File(dir, weightFile)
            // 模型结构文件至少几百 KB；权重至少 1MB（最小的 Pocket 也有 166MB）
            m.isFile && m.length() > 100_000L && w.isFile && w.length() > 1_000_000L
        }.getOrDefault(false)
    }

    /** 已下载的模型清单 */
    fun downloadedSpecs(context: Context): List<LocalModelSpec> =
        CATALOG.filter { modelDownloaded(context, it.id) }

    /** 当前生效的模型：优先用户选的，其次任意已下载的，都没有则给默认（Qwen2.5-1.5B） */
    fun activeSpec(context: Context): LocalModelSpec {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE, null)
        if (saved != null) {
            CATALOG.firstOrNull { it.id == saved && modelDownloaded(context, it.id) }?.let { return it }
        }
        return downloadedSpecs(context).firstOrNull() ?: CATALOG[0]
    }

    /** 切换当前模型；换了模型必须丢弃旧会话，否则 KV cache 是上一个模型的 */
    fun setActive(context: Context, id: String) {
        if (activeSpec(context).id == id) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACTIVE, id).apply()
        releaseSession()
    }

    fun state(context: Context): LocalLlmState = when {
        !engineAvailable() -> LocalLlmState.ENGINE_MISSING
        downloadedSpecs(context).isEmpty() -> LocalLlmState.ENGINE_READY_MODEL_MISSING
        else -> LocalLlmState.READY
    }

    /** 单个模型已占用磁盘大小（MB） */
    fun modelSizeMb(context: Context, id: String): Long {
        val dir = modelDir(context, id)
        if (!dir.isDirectory) return 0
        var bytes = 0L
        dir.walkTopDown().forEach { if (it.isFile) bytes += it.length() }
        return bytes / (1024 * 1024)
    }

    /** 全部模型总占用（MB），设置页副标题用 */
    fun totalSizeMb(context: Context): Long =
        CATALOG.sumOf { modelSizeMb(context, it.id) }

    // ---------- 运行内存预算 / 上下文 ----------

    /** 模型 config.json 自带的上下文上限；没有就返回 0（未知，不夹） */
    private fun baseMaxAllTokens(context: Context, id: String): Int = runCatching {
        val cfg = File(modelDir(context, id), "config.json")
        if (!cfg.isFile) 0 else JSONObject(cfg.readText()).optInt("max_all_tokens", 0)
    }.getOrDefault(0)

    /** 当前预算下，这个模型的大致运行占用（权重 + 上下文 KV） */
    fun estimatedMemoryMb(
        context: Context,
        id: String,
        contextTokens: Int = LocalAiPrefs.effectiveContextTokens(context, "", 0, 0, 0)
    ): Int {
        val spec = CATALOG.firstOrNull { it.id == id } ?: return 0
        return LocalAiPolicy.estimateRuntimeMb(spec.approxMb, contextTokens)
    }

    /** 当前分配的内存预算装不装得下这个模型（首页/模型列表据此提示） */
    fun fitsMemoryBudget(
        context: Context,
        id: String,
        contextTokens: Int = LocalAiPrefs.effectiveContextTokens(context, "", 0, 0, 0)
    ): Boolean {
        val spec = CATALOG.firstOrNull { it.id == id } ?: return true
        return LocalAiPolicy.fitsMemoryBudget(
            modelMb = spec.approxMb,
            budgetMb = LocalAiPrefs.memoryBudgetMb(context),
            contextTokens = contextTokens
        )
    }

    /**
     * 写一份带本次上下文上限的运行配置。
     *
     * 不改原始 config.json：下载完整性校验、用户导入的模型都继续按原文件判断；
     * 只在同目录写 config.runtime.json，MNN 读它时相对路径仍然有效。
     */
    private fun runtimeConfigPath(context: Context, id: String, contextTokens: Int): String {
        val dir = modelDir(context, id)
        val base = File(dir, "config.json")
        val fallback = base.absolutePath
        val obj = runCatching { JSONObject(base.readText()) }.getOrElse { return fallback }
        val baseMax = obj.optInt("max_all_tokens", 0)
        val maxAll = if (baseMax > 0) minOf(contextTokens, baseMax) else contextTokens
        obj.put("max_all_tokens", maxAll)
        val runtime = File(dir, "config.runtime.json")
        return runCatching {
            runtime.writeText(obj.toString())
            runtime.absolutePath
        }.getOrDefault(fallback)
    }

    // ---------- 下载（ModelScope -> 私有目录，完成即自动启用） ----------

    fun isDownloading(id: String): Boolean = downloadProgress.containsKey(id)

    /** 开始下载；重复调用、已完整下载时直接忽略 */
    fun startDownload(context: Context, spec: LocalModelSpec) {
        if (downloadJobs.containsKey(spec.id) || modelDownloaded(context, spec.id)) return
        val appCtx = context.applicationContext
        downloadError = downloadError - spec.id
        downloadJobs[spec.id] = downloadScope.launch {
            try {
                // 先清掉该模型的半成品目录（上次断网留下 config.json 但没权重的残骸），
                // 避免"假下载"：config.json 在、权重缺，却被误判成已下载。
                modelDir(appCtx, spec.id).deleteRecursively()
                downloadInternal(appCtx, spec) { p ->
                    downloadProgress = downloadProgress + (spec.id to p)
                }
                // 下载完成自动接管为当前模型，无需用户再点一次
                setActive(appCtx, spec.id)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                downloadError = downloadError + (spec.id to (t.message ?: "下载失败"))
                // 清掉半成品 .part，保留已完成的文件不影响重下（重下会覆盖）
                modelDir(appCtx, spec.id).listFiles()
                    ?.filter { it.name.endsWith(".part") }
                    ?.forEach { it.delete() }
            } finally {
                downloadProgress = downloadProgress - spec.id
                downloadJobs.remove(spec.id)
            }
        }
    }

    /** 取消进行中的下载 */
    fun cancelDownload(id: String) {
        downloadJobs.remove(id)?.cancel()
    }

    private fun httpGet(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            // 大权重在手机弱网下可能传很久，读超时放宽到 60s（两次读之间的间隔，不是总时长）
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MYFootprints/1.0")
        }

    private data class RemoteFile(val path: String, val size: Long)

    /** 拉仓库文件清单 -> 并行下载 -> 落盘改名。全程可取消（协程取消即中断） */
    private suspend fun downloadInternal(context: Context, spec: LocalModelSpec, onProgress: (Int) -> Unit) {
        // 1. 文件清单：只取根目录的实体文件（跳过 README、.gitattributes、.msc/.mv 等元数据）
        val listConn = httpGet("https://modelscope.cn/api/v1/models/${spec.repo}/repo/files?Recursive=true")
        if (listConn.responseCode !in 200..299) throw IOException("模型清单请求失败：HTTP ${listConn.responseCode}")
        val body = listConn.inputStream.bufferedReader().use { it.readText() }
        val files = JSONObject(body).getJSONObject("Data").getJSONArray("Files")
        val remote = buildList {
            for (i in 0 until files.length()) {
                val o = files.getJSONObject(i)
                if (o.optString("Type") != "blob") continue
                val p = o.getString("Path")
                if (p.contains('/') || p.startsWith('.') || p.endsWith(".md", true)) continue
                add(RemoteFile(p, o.optLong("Size", 0L)))
            }
        }
        if (remote.none { it.path == "config.json" }) throw IOException("仓库里没找到 config.json")

        // 2. 并行下载：最多 4 路并发，断点续传（Range），256KB 缓冲
        val total = remote.sumOf { it.size }.coerceAtLeast(1)
        val done = AtomicLong(0)
        val dir = modelDir(context, spec.id)
        dir.mkdirs()
        onProgress(0)

        // 已完成的文件不计入进度（续传时跳过）
        remote.forEach { rf ->
            val out = File(dir, rf.path)
            if (out.isFile && (rf.size == 0L || out.length() == rf.size)) {
                done.addAndGet(rf.size)
            }
        }
        onProgress(((done.get() * 100) / total).toInt().coerceIn(0, 99))

        // 并行下载未完成的文件（限制并发数，避免同时开太多连接）
        coroutineScope {
            val active = remote.filter { rf ->
                val out = File(dir, rf.path)
                !(out.isFile && (rf.size == 0L || out.length() == rf.size))
            }
            // 简单限流：每批最多 4 个
            active.chunked(4).forEach { batch ->
                batch.map { rf ->
                    async(Dispatchers.IO) {
                        downloadOneFile(spec.repo, rf, dir, done, total, onProgress)
                    }
                }.forEach { it.await() }
            }
        }
        onProgress(100)
        if (!File(dir, "config.json").isFile) throw IOException("下载完成但缺少 config.json")
        // 最终兜底：重新校验权重，不完整就抛错（触发外层提示重试）
        if (!modelDownloaded(context, spec.id)) throw IOException("下载完成但模型校验未通过（权重缺失）")
    }

    /** 单文件下载，支持断点续传（HTTP Range），256KB 缓冲 */
    private suspend fun downloadOneFile(
        repo: String, rf: RemoteFile, dir: File, done: AtomicLong, total: Long, onProgress: (Int) -> Unit
    ) {
        val out = File(dir, rf.path)
        val tmp = File(dir, rf.path + ".part")
        val existing = if (tmp.isFile) tmp.length() else 0L

        val c = httpGet("https://modelscope.cn/models/$repo/resolve/master/${rf.path}")
        // 断点续传：如果 .part 已有部分数据，从 existing 处继续
        if (existing > 0L && rf.size > 0L && existing < rf.size) {
            c.setRequestProperty("Range", "bytes=$existing-")
        }
        if (c.responseCode !in 200..299 && c.responseCode != 206) {
            throw IOException("${rf.path}：HTTP ${c.responseCode}")
        }
        c.inputStream.use { input ->
            // 续传时追加写入，新下载时覆盖
            val fos = java.io.FileOutputStream(tmp, existing > 0L && c.responseCode == 206)
            fos.use { output ->
                val buf = ByteArray(1 shl 18) // 256KB 缓冲
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    done.addAndGet(n.toLong())
                    onProgress(((done.get() * 100) / total).toInt().coerceIn(0, 99))
                }
            }
        }
        if (!tmp.renameTo(out)) throw IOException("${rf.path} 落盘失败")
        // 字节数对不上说明被截断，立刻报错重下
        if (rf.size > 0L && out.length() != rf.size) {
            out.delete()
            throw IOException("${rf.path} 下载不完整（${out.length()}/${rf.size} 字节），已作废请重试")
        }
    }

    // ---------- 手工导入（已解压的模型文件夹，作为下载通道的补充） ----------

    /**
     * 从系统文件选择器选中的目录导入模型（默认导入为 Qwen2.5-1.5B —— 官方渠道
     * 下载解压的就是它；导入完成自动设为当前模型）。
     */
    suspend fun importFromTree(context: Context, treeUri: Uri, onProgress: (Int) -> Unit = {}): Boolean =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext false
            if (!root.isDirectory) return@withContext false
            val target = modelDir(context, CATALOG[0].id)
            // 覆盖式导入：先清掉旧模型，避免半旧半新导致加载失败
            target.deleteRecursively()
            target.mkdirs()

            val files = root.listFiles()
            val total = files.size.coerceAtLeast(1)
            var done = 0
            files.forEach { f ->
                if (f.isFile) {
                    val name = f.name ?: return@forEach
                    val out = File(target, name)
                    runCatching {
                        context.contentResolver.openInputStream(f.uri)?.use { input ->
                            out.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
                        }
                    }
                    done++
                    onProgress((done * 100 / total).coerceIn(0, 100))
                }
            }
            if (!File(target, "config.json").isFile) return@withContext false
            setActive(context, CATALOG[0].id)
            true
        }

    /** 删除某个已下载的模型；删的是当前/正在使用的模型时顺手释放会话 */
    fun deleteModel(context: Context, id: String): Boolean {
        if (activeSpec(context).id == id || sessionModelId == id) releaseSession()
        return modelDir(context, id).deleteRecursively()
    }

    /** 释放原生会话（切模型、删模型、每次推理结束时调用，把权重占的内存还回去） */
    fun releaseSession() {
        synchronized(sessionLock) { releaseSessionLocked() }
    }

    private fun releaseSessionLocked() {
        if (session != 0L) {
            runCatching { MnnLlmBridge.nativeRelease(session) }
            session = 0L
        }
        sessionModelId = null
        sessionConfigKey = null
    }

    /**
     * 本地推理入口。未就绪时返回 null，调用方据此决定回退云端还是报错。
     *
     * [modelId] 为空时用当前启用模型；指定某个已下载模型时按它推理。
     * 端侧引擎同时只保留一个会话，换模型会先释放旧权重再加载新模型。
     *
     * 首次调用要加载近 1GB 权重，会停顿数秒；生成阶段在 CPU 上也是秒级起步，
     * 所以内部切到 IO 线程，绝不占用主线程。
     *
     * 返回格式：思考部分以 \u0001 前缀，正文在后面。
     */
    suspend fun chatOnce(context: Context, prompt: String, modelId: String? = null): String? {
        if (prompt.isBlank()) return null
        if (state(context) != LocalLlmState.READY) return null
        val spec = modelId
            ?.let { id -> CATALOG.firstOrNull { it.id == id } }
            ?.takeIf { modelDownloaded(context, it.id) }
            ?: activeSpec(context)
        // 上下文预算：先把用户「自动/手动」的档位解出来，再保证至少装得下本次输入。
        // max_new_tokens 仍固定 512（输出上限），上下文是输入 + KV cache 的预算，两件事分开。
        val baseMax = baseMaxAllTokens(context, spec.id)
        val requestedCtx = LocalAiPrefs.effectiveContextTokens(
            context = context,
            userText = prompt,
            contextChars = 0,
            historyTurns = 0,
            modelLimit = baseMax
        )
        val contextTokens = LocalAiPolicy.localContextTokens(
            requestedTokens = requestedCtx,
            promptChars = prompt.length,
            maxNewTokens = MAX_NEW_TOKENS,
            modelLimit = baseMax
        )
        // 内存预算不够就直接判不加载，让调用方回退云端，而不是由系统 OOM 杀进程。
        if (!LocalAiPolicy.fitsMemoryBudget(
                modelMb = spec.approxMb,
                budgetMb = LocalAiPrefs.memoryBudgetMb(context),
                contextTokens = contextTokens
            )
        ) return null

        val configKey = "${spec.id}:$contextTokens"
        val configPath = runtimeConfigPath(context, spec.id, contextTokens)
        return withContext(Dispatchers.IO) {
            synchronized(sessionLock) {
                try {
                    runCatching {
                        if (session != 0L && (sessionModelId != spec.id || sessionConfigKey != configKey)) {
                            // 换了模型或改了上下文：旧会话的 KV cache 不能复用，先释放权重
                            releaseSessionLocked()
                        }
                        if (session == 0L) {
                            val created = MnnLlmBridge.nativeCreate(configPath)
                            if (created == 0L) return@runCatching null
                            if (!MnnLlmBridge.nativeLoad(created)) {
                                MnnLlmBridge.nativeRelease(created)
                                return@runCatching null
                            }
                            session = created
                            sessionModelId = spec.id
                            sessionConfigKey = configKey
                        }
                        MnnLlmBridge.nativeGenerate(session, prompt, MAX_NEW_TOKENS)
                            ?.let { raw ->
                                // 返回格式约定（供调用方按 \u0001 拆分）：
                                //   [\u0001 思考 \u0001 正文] —— 有完整 <think>...</think>
                                //   [\u0001 思考]            —— 思考没写完就撞 token 上限，没有正文
                                //   [正文]                   —— 模型没输出思考标签
                                val cleaned = raw.replace("<|im_end|>", "")
                                val out = StringBuilder()
                                val idx = cleaned.indexOf("<think>")
                                if (idx >= 0) {
                                    val close = cleaned.indexOf("</think>", idx)
                                    if (close >= 0) {
                                        val think = cleaned.substring(idx + "<think>".length, close).trim()
                                        val answer = cleaned.substring(close + "</think>".length).trim()
                                        if (think.isNotEmpty()) out.append("\u0001").append(think).append("\u0001")
                                        out.append(answer)
                                    } else {
                                        // 没闭合：整段都是思考，正文为空
                                        val think = cleaned.substring(idx + "<think>".length).trim()
                                        if (think.isNotEmpty()) out.append("\u0001").append(think)
                                    }
                                } else {
                                    out.append(cleaned.trim())
                                }
                                out.toString().trim()
                            }
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                } finally {
                    // 每次推理结束都立即释放权重，不能等下次切模型才还内存
                    releaseSessionLocked()
                }
            }
        }
    }
}
