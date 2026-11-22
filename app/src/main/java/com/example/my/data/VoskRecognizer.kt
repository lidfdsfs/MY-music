package com.example.my.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

/**
 * Vosk 离线中文语音识别封装。
 * 模型首次使用前需下载（约 42MB），下载后解压到 app 私有目录。
 *
 * 下载不走 Vosk 自带的 StorageService：那条路径一旦失败只回调一个「失败」，
 * 既没有进度也说不清原因（超时？HTTP 403？半截包？），用户在设置里只看到
 * 「下载失败 · 点击重试」。这里自己开连接、自己解压、自己校验：
 * 拿到真实 HTTP 状态码 / 字节数 / 解压完整性，失败原因写进 [lastError]。
 */
class VoskRecognizer(private val context: Context) {

    companion object {
        /**
         * 模型下载源，按顺序尝试。
         *
         * 官方 alphacephei 在国内实测只有 ~14 KB/s（42MB 要下近一小时，用户看到的就是
         * 「下载失败」）；hf-mirror 上这份同版本中文 small 模型（README 里 CER 23.54% /
         * 38.29% 与官方 vosk-model-small-cn-0.22 一致）实测 14 秒下完，故放第一位，
         * 官方源留作兜底。
         */
        private val MODEL_URLS = listOf(
            "https://hf-mirror.com/guloooovoooo/vosk-model-small-cn/resolve/main/vosk-model-small-cn.zip",
            "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
        )
        private const val MODEL_DIR = "vosk-cn-model"
        /** 模型包解压后必须有的文件，用来判断「下完了」而不是「下了一半」 */
        private const val SENTINEL = "conf/mfcc.conf"
        /** 真正的声学模型，缺它就一定跑不起来 */
        private const val MODEL_WEIGHTS = "am/final.mdl"
    }

    private var model: Model? = null
    private var speechService: SpeechService? = null
    var ready: Boolean = false
        private set

    /** 最近一次下载失败的原因；成功时清空。UI 直接展示给用户 */
    @Volatile
    var lastError: String? = null
        private set

    /** 模型是否已下载解压 */
    fun hasModel(): Boolean {
        val dir = File(context.filesDir, MODEL_DIR)
        return File(dir, SENTINEL).exists() && File(dir, MODEL_WEIGHTS).exists()
    }

    /** 初始化模型（在 IO 线程调用）；[onResult] 回到主线程 */
    fun init(onResult: (Boolean) -> Unit) {
        if (ready) { onResult(true); return }
        val main = Handler(Looper.getMainLooper())
        Thread {
            var ok = false
            try {
                val dir = File(context.filesDir, MODEL_DIR)
                if (dir.exists()) {
                    model = Model(dir.absolutePath)
                    ready = true
                    ok = true
                }
            } catch (e: Exception) {
                ready = false
                lastError = e.message ?: e.javaClass.simpleName
            }
            val result = ok
            main.post { onResult(result) }
        }.start()
    }

    /** 异步下载模型（约 42MB），进度回调 0-100（主线程），结束回调是否成功 */
    fun downloadModel(onProgress: (Int) -> Unit, onDone: (Boolean) -> Unit) {
        lastError = null
        val main = Handler(Looper.getMainLooper())
        Thread {
            val zip = File(context.cacheDir, "vosk-model-cn-0.22.zip")
            var error: String? = null
            try {
                downloadWithRetry(zip) { pct -> main.post { onProgress(pct) } }
                extract(zip)
                if (!hasModel()) error = "解压完成但模型文件不全（缺 am/final.mdl 或 conf/mfcc.conf）"
            } catch (e: Exception) {
                error = describe(e)
            } finally {
                runCatching { zip.delete() }
            }
            lastError = error
            val ok = error == null
            main.post { onDone(ok) }
        }.start()
    }

    /**
     * 逐个源尝试：连不上 / 半截包先续传重试一次，还不行就换下一个源，全部失败才报错。
     * 换源时必须把上一段残包删掉，否则两个源的字节拼在一起会变成坏包。
     */
    private fun downloadWithRetry(dest: File, onProgress: (Int) -> Unit) {
        var last: Exception? = null
        for (url in MODEL_URLS) {
            var ok = false
            var attempts = 0
            while (attempts < 2 && !ok) {
                attempts++
                try {
                    fetch(url, dest, onProgress)
                    ok = true
                } catch (e: Exception) {
                    last = e
                    onProgress(0)
                }
            }
            if (ok) return
            dest.delete()
        }
        throw last ?: IOException("下载失败")
    }

    private fun openConnection(url: String, offset: Long): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            // 部分 CDN 对空 UA 直接 403，带上一个正常客户端标识
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) MYFootprint/1.0")
            // 42MB 在手机上很容易被切网打断：支持续传的源就从断点接着下，不用从头再来
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }

    /**
     * 下载到 [dest]，支持断点续传。
     *
     * 旧版最大的坑是分不清「下完了」和「下到一半断线」：服务端不返回 Content-Length
     * （chunked）时，断线只表现为流读完，一个少了尾巴的 zip 被当成完整包解压，
     * 最后表现成「压缩包里没有模型文件」。这里两道防线：
     * ① 有 Content-Length 就核字节数；② 没有就交给 extract 打开 zip 时暴露坏包。
     */
    private fun fetch(url: String, dest: File, onProgress: (Int) -> Unit) {
        var have = if (dest.exists()) dest.length() else 0L
        var conn = openConnection(url, have)
        try {
            var code = conn.responseCode
            // 服务器不认 Range（回 200 而不是 206）就从零重下，别把两段拼成一个坏包
            if (have > 0 && code != 206) {
                runCatching { conn.disconnect() }
                have = 0L
                dest.delete()
                conn = openConnection(url, 0)
                code = conn.responseCode
            }
            if (code !in 200..299) throw IOException("服务器返回 HTTP $code")
            val remain = conn.contentLengthLong
            val total = if (code == 206 && remain > 0) have + remain else remain
            var read = have
            var lastPct = if (total > 0) ((have * 100) / total).toInt() else -1
            conn.inputStream.use { raw ->
                BufferedInputStream(raw, 64 * 1024).use { input ->
                    FileOutputStream(dest, have > 0).use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val pct = ((read * 100) / total).toInt().coerceIn(0, 100)
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(pct)
                                }
                            }
                        }
                        out.flush()
                    }
                }
            }
            if (total > 0 && dest.length() != total) {
                throw IOException("下载中断：只收到 ${dest.length() / 1024}KB / ${total / 1024}KB")
            }
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /**
     * 解压到临时目录 → 校验 → 整体换名。
     * 任何一步出错都不会把一份半残的模型留在 [MODEL_DIR] 里被当成「已下载」。
     *
     * 用 [ZipFile]（读中央目录）而不是 ZipInputStream：流式读到一半断包时它会安静地
     * 停在半路，最后只表现为「没有模型文件」；ZipFile 打开就会明确报坏包。
     * 另外不同镜像站的包结构不统一（有的外面套一层 vosk-model-small-cn/，有的不套），
     * 所以先算出共同的顶层目录再剥，最后再按 conf/mfcc.conf 的真实位置反推模型根。
     */
    private fun extract(zip: File) {
        val target = File(context.filesDir, MODEL_DIR)
        val tmp = File(context.filesDir, "$MODEL_DIR.tmp")
        tmp.deleteRecursively()
        tmp.mkdirs()
        var written = 0
        try {
            ZipFile(zip).use { zipFile ->
                val prefix = commonTopDir(zipFile)
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name.replace('\\', '/')
                    val rel = if (prefix != null) name.removePrefix(prefix) else name
                    if (entry.isDirectory || rel.isBlank()) continue
                    val leaf = rel.substringAfterLast('/')
                    // 镜像站的包带 macOS 附属文件（__MACOSX/、._xxx、.DS_Store），不进模型目录
                    if (rel.startsWith("__MACOSX/") || leaf.startsWith("._") || leaf == ".DS_Store") continue
                    val out = File(tmp, rel)
                    // 防 zip 路径穿越：解出来的文件必须还在临时目录里
                    val base = tmp.canonicalPath + File.separator
                    if (!out.canonicalPath.startsWith(base)) throw IOException("压缩包路径非法：$name")
                    out.parentFile?.mkdirs()
                    zipFile.getInputStream(entry).use { input ->
                        FileOutputStream(out).use { fileOut -> input.copyTo(fileOut, 64 * 1024) }
                    }
                    written++
                }
            }
        } catch (e: java.util.zip.ZipException) {
            throw IOException("下载到的不是完整压缩包（可能中途断网），请重试")
        }
        val root = findModelRoot(tmp) ?: throw IOException(
            "压缩包里没有模型文件（解出 $written 个文件：${topNames(tmp)}）"
        )
        target.deleteRecursively()
        if (root == tmp) {
            if (!tmp.renameTo(target)) {
                // rename 在同卷内应该成功；失败了就退化成逐个搬
                tmp.copyRecursively(target, overwrite = true)
                tmp.deleteRecursively()
            }
        } else {
            // 模型被多套了一层目录：把真正的模型根整体换成目标目录
            if (!root.renameTo(target)) {
                root.copyRecursively(target, overwrite = true)
                root.deleteRecursively()
            }
            tmp.deleteRecursively()
        }
    }

    /** 包内所有条目共同的顶层目录（如 vosk-model-small-cn/）；没有就返回 null */
    private fun commonTopDir(zipFile: java.util.zip.ZipFile): String? {
        val tops = HashSet<String>()
        var nested = false
        val entries = zipFile.entries()
        while (entries.hasMoreElements()) {
            val n = entries.nextElement().name.replace('\\', '/')
            if (n.isBlank()) continue
            tops.add(n.substringBefore('/'))
            if (n.contains('/')) nested = true
        }
        return if (nested && tops.size == 1) tops.first() + "/" else null
    }

    /** 找模型根：优先临时目录本身，找不到就按 conf/mfcc.conf 的实际位置往上一层反推 */
    private fun findModelRoot(dir: File): File? {
        if (File(dir, SENTINEL).exists() && File(dir, MODEL_WEIGHTS).exists()) return dir
        var found: File? = null
        dir.walkTopDown().forEach { f ->
            if (found == null && f.isFile && f.name == "mfcc.conf" && f.parentFile?.name == "conf") {
                val root = f.parentFile?.parentFile
                if (root != null && File(root, MODEL_WEIGHTS).exists()) found = root
            }
        }
        return found
    }

    /** 失败信息里带上解出来的顶层目录，用户截图就能看出包结构问题 */
    private fun topNames(dir: File): String =
        (dir.list()?.joinToString("、") ?: "空").take(80)

    private fun describe(e: Exception): String = when (e) {
        is java.net.SocketTimeoutException -> "连接超时，检查网络后重试"
        is java.net.UnknownHostException -> "域名解析失败，检查网络或换 Wi-Fi"
        is IOException -> e.message ?: "下载失败，请重试"
        else -> e.message ?: e.javaClass.simpleName
    }

    /**
     * 开始录音识别，回调识别到的文字。
     *
     * 回调一律回到主线程再交给 UI：Vosk 的识别跑在它自己的录音线程里，
     * 直接在那儿改 Compose 状态容易撞上「状态被后台线程写」的问题。
     * 失败（没权限 / 模型没加载）走 [onError]，不让界面停在「正在录音」的假状态。
     */
    fun start(onResult: (String) -> Unit, onError: (String) -> Unit = {}) {
        val m = model
        if (m == null) {
            onError("离线语音模型还没加载好，稍后再试或重新下载")
            return
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onError("未授权录音权限")
            return
        }

        val main = Handler(Looper.getMainLooper())
        val recognizer = Recognizer(m, 16000.0f)
        speechService = SpeechService(recognizer, 16000.0f)
        speechService?.startListening(object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                // 中间结果不往输入框里塞，避免半句话反复跳
            }

            override fun onResult(hypothesis: String?) {
                hypothesis?.let { text ->
                    if (text.isNotBlank()) main.post { onResult(text) }
                }
            }

            override fun onFinalResult(hypothesis: String?) {
                hypothesis?.let { text ->
                    if (text.isNotBlank()) main.post { onResult(text) }
                }
            }

            override fun onError(exception: Exception?) {
                val msg = exception?.message ?: "识别出错"
                main.post { onError(msg) }
            }

            override fun onTimeout() {}
        })
    }

    private var wakeService: SpeechService? = null

    /**
     * 语音唤醒监听：持续听，识别结果（含中间结果）里出现 [phrase] 就停下并回调 [onHit]。
     * 只在 App 前台调用；[stopWakeWatch] 会立即释放录音与识别器，退后台零开销。
     *
     * 省电：中间结果每秒会来好几次，逐个做 JSON 解析 + 唤醒词滑窗匹配纯属浪费 CPU，
     * 这里节流到约 300ms 判断一次（命中延迟最多多 0.3 秒），整句结果仍立即判断。
     */
    fun watchWakeWord(
        phraseOf: () -> String,
        onHit: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        if (wakeService != null) return
        val m = model
        if (m == null) {
            onError("离线语音模型还没加载好")
            return
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onError("未授权录音权限")
            return
        }
        if (WakeWord.normalize(phraseOf()).isBlank()) {
            onError("唤醒词为空")
            return
        }
        val main = Handler(Looper.getMainLooper())
        val recognizer = Recognizer(m, 16000.0f).apply {
            // 只关心「这句话里有没有唤醒词」：不要词级时间戳和候选，返回的 JSON 更小、计算更省
            runCatching { setWords(false) }
            runCatching { setPartialWords(false) }
            runCatching { setMaxAlternatives(0) }
        }
        val svc = SpeechService(recognizer, 16000.0f)
        wakeService = svc
        val checkNow: (String?) -> Unit = { hypothesis ->
            val text = voskText(hypothesis)
            if (text.isNotBlank() && WakeWord.matches(text, phraseOf())) {
                main.post {
                    if (wakeService === svc) {
                        stopWakeWatch()
                        onHit()
                    }
                }
            }
        }
        var lastPartialCheck = 0L
        val checkPartial: (String?) -> Unit = { hypothesis ->
            val now = SystemClock.uptimeMillis()
            if (now - lastPartialCheck >= 300L) {
                lastPartialCheck = now
                checkNow(hypothesis)
            }
        }
        svc.startListening(object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) = checkPartial(hypothesis)
            override fun onResult(hypothesis: String?) = checkNow(hypothesis)
            override fun onFinalResult(hypothesis: String?) = checkNow(hypothesis)
            override fun onError(exception: Exception?) {
                main.post {
                    if (wakeService === svc) {
                        stopWakeWatch()
                        onError(exception?.message ?: "识别出错")
                    }
                }
            }
            override fun onTimeout() {}
        })
    }

    /** 停掉唤醒监听并释放识别器（不释放模型，模型留给下次复用） */
    fun stopWakeWatch() {
        val svc = wakeService ?: return
        wakeService = null
        runCatching { svc.stop() }
        runCatching { svc.shutdown() }
    }

    /** Vosk 回调是 JSON：中间结果在 partial，整句在 text */
    private fun voskText(json: String?): String {
        if (json.isNullOrBlank()) return ""
        return runCatching {
            val o = org.json.JSONObject(json)
            o.optString("partial").ifBlank { o.optString("text") }
        }.getOrDefault("")
    }

    fun stop() {
        speechService?.stop()
        speechService = null
    }

    fun release() {
        stop()
        model?.close()
        model = null
        ready = false
    }
}
