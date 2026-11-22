package com.example.my.data.ai

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.my.data.VoskRecognizer
import java.util.Locale

/**
 * 语音能力封装：
 *  - 语音输入：优先用 Vosk 离线识别（模型已下载时），否则回退系统 SpeechRecognizer；
 *  - 语音播报：系统 TTS，按「语音库」里选中的声音包（音色 / 语速 / 音调）朗读 AI 回复。
 */
object VoiceIo {

    // Vosk 离线识别器（单例，懒加载）
    private var vosk: VoskRecognizer? = null
    private var voskInitialized = false

    /** Vosk 离线识别是否可用（模型已下载且初始化成功） */
    fun voskAvailable(context: Context): Boolean {
        if (voskInitialized) return vosk != null
        return runCatching {
            VoskRecognizer(context.applicationContext).hasModel()
        }.getOrDefault(false)
    }

    /** 用 Vosk 离线识别一段语音，识别完成回调结果文本 */
    fun startVoskRecognition(context: Context, onResult: (String) -> Unit, onError: (String) -> Unit) {
        runCatching {
            val v = vosk ?: VoskRecognizer(context.applicationContext).also { vosk = it }
            if (!v.hasModel()) {
                onError("离线语音模型未下载，请在设置 - 语音识别里下载")
                return
            }
            v.init { ready ->
                if (!ready) { onError("离线语音模型加载失败"); return@init }
                v.start(
                    onResult = { text ->
                        if (text.isNotBlank()) {
                            onResult(text)
                            v.stop()
                        }
                    },
                    onError = { msg -> onError(msg) }
                )
            }
        }.onFailure { onError("离线语音识别启动失败：${it.message}") }
    }

    fun stopVosk() {
        vosk?.stop()
    }

    // ---------- 语音播报 ----------

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    /** 引擎初始化失败时的状态码；null 表示还没失败过 */
    private var ttsFailCode: Int? = null
    private var pendingText: String? = null
    private var pendingLocale: Locale = Locale.SIMPLIFIED_CHINESE
    private var pendingPack: VoicePack? = null
    private val voiceListeners = mutableListOf<(List<VoiceInfo>) -> Unit>()

    /**
     * 播报一段文字；[language] 取 "zh" / "en"，[pack] 非空时用于「试听」指定声音包，
     * 为空则使用语音库里当前选中的声音。
     *
     * 返回 false 表示这台机器根本没有可用的 TTS 引擎——调用方要给出提示，
     * 而不是像以前那样静默失败，让人以为是「语音坏了」。
     */
    fun speak(context: Context, text: String, language: String = "zh", pack: VoicePack? = null): Boolean {
        val clean = cleanForSpeech(text)
        if (clean.isBlank()) return true
        val app = context.applicationContext
        val locale = if (language.startsWith("en")) Locale.US else Locale.SIMPLIFIED_CHINESE
        val engine = ensureEngine(app) ?: return false
        // 之前已经确定引擎起不来：不要再排一条永远念不出来的待播文本
        if (ttsFailCode != null) return false
        if (!ttsReady) {
            pendingText = clean
            pendingLocale = locale
            pendingPack = pack
            return true
        }
        applyVoice(engine, app, locale, pack)
        engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "my_footprint_tts")
        return true
    }

    /** 试听某个声音包（不会改掉当前选择） */
    fun preview(context: Context, pack: VoicePack, text: String = VoiceLibrary.PREVIEW_TEXT): Boolean =
        speak(context, text, if (langOfPack(pack).startsWith("en")) "en" else "zh", pack)

    private fun langOfPack(pack: VoicePack): String = pack.locale.lowercase().replace('_', '-')

    /**
     * 把「声音包」落到系统 TTS 上：优先 setVoice 到匹配的已安装声音，
     * 找不到就退回语言 + pitch/rate 微调（内置音色包靠这一步也能听出区别）。
     */
    private fun applyVoice(engine: TextToSpeech, app: Context, locale: Locale, override: VoicePack?) {
        val pack = override ?: VoiceLibrary.activePack(app)
        val installed = runCatching { toVoiceInfoList(engine.voices) }.getOrDefault(emptyList())
        val systemId = if (VoiceLibrary.isSystemVoice(pack.id)) VoiceLibrary.systemVoiceId(pack.id) else null
        val chosen = if (systemId != null) installed.firstOrNull { it.id == systemId }
        else VoiceLibrary.matchVoice(pack, installed)
        val actual = chosen?.let { c -> runCatching { engine.voices?.firstOrNull { it.name == c.id } }.getOrNull() }
        // 先换音色、再设语速音调：不少引擎的 setVoice 会把已设的 pitch / rate 打回默认，
        // 顺序反了就会出现「四个音色试听一模一样」。
        if (actual != null) {
            runCatching { engine.setVoice(actual) }
        } else {
            runCatching { engine.language = locale }
        }
        runCatching {
            engine.setSpeechRate((VoiceLibrary.rate(app) * pack.rate).coerceIn(0.5f, 2.0f))
            engine.setPitch((VoiceLibrary.pitch(app) * pack.pitch).coerceIn(0.5f, 2.0f))
        }
    }

    /** 列出系统 TTS 引擎已安装的声音包；引擎还在初始化时排队，就绪后回调 */
    fun listVoices(context: Context, onResult: (List<VoiceInfo>) -> Unit) {
        val app = context.applicationContext
        val engine = ensureEngine(app) ?: run { onResult(emptyList()); return }
        if (!ttsReady) {
            synchronized(voiceListeners) { voiceListeners.add(onResult) }
            return
        }
        onResult(runCatching { toVoiceInfoList(engine.voices) }.getOrDefault(emptyList()))
    }

    /**
     * 系统语音数据安装完成后重建 TTS 实例再扫描：
     * 引擎会缓存 voice 列表，不重建的话刚下载的声音包可能读不到。
     */
    fun refreshVoices(context: Context, onResult: (List<VoiceInfo>) -> Unit) {
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
        ttsFailCode = null
        pendingText = null
        pendingPack = null
        synchronized(voiceListeners) { voiceListeners.clear() }
        listVoices(context, onResult)
    }

    private fun toVoiceInfoList(set: Set<android.speech.tts.Voice>?): List<VoiceInfo> =
        set.orEmpty().map { v ->
            VoiceInfo(
                id = v.name,
                name = v.name,
                locale = v.locale?.toString().orEmpty(),
                quality = v.quality,
                needsNetwork = v.features
                    ?.contains(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS) == true
            )
        }

    fun stop() {
        pendingText = null
        pendingPack = null
        runCatching { tts?.stop() }
        vosk?.stop()
    }

    fun shutdown() {
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
        ttsFailCode = null
        pendingText = null
        pendingPack = null
        synchronized(voiceListeners) { voiceListeners.clear() }
        vosk?.release()
        vosk = null
    }

    private fun ensureEngine(app: Context): TextToSpeech? {
        tts?.let { return it }
        return runCatching {
            TextToSpeech(app) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (!ttsReady) {
                    // 引擎没起来（系统没装 TTS / 数据包缺失）：记下状态码，后续 speak 直接返回 false
                    ttsFailCode = status
                    pendingText = null
                    pendingPack = null
                }
                if (ttsReady) {
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {}
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {}
                    })
                    val waiting = synchronized(voiceListeners) {
                        voiceListeners.toList().also { voiceListeners.clear() }
                    }
                    val voices = runCatching { toVoiceInfoList(tts?.voices) }.getOrDefault(emptyList())
                    waiting.forEach { it(voices) }
                    val text = pendingText
                    val locale = pendingLocale
                    val pack = pendingPack
                    pendingText = null
                    pendingPack = null
                    if (text != null) {
                        tts?.let { eng ->
                            applyVoice(eng, app, locale, pack)
                            eng.speak(text, TextToSpeech.QUEUE_FLUSH, null, "my_footprint_tts")
                        }
                    }
                }
            }.also { tts = it }
        }.getOrNull()
    }

    private fun cleanForSpeech(raw: String): String =
        raw.lineSequence()
            .filterNot { it.trimStart().startsWith("[记忆]") || it.trimStart().startsWith("[追问]") }
            .joinToString(" ")
            .replace(Regex("<intent>.*?</intent>|<action>.*?</action>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""[*_#`>~]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    // ---------- 语音输入（系统后备） ----------

    fun recognitionAvailable(context: Context): Boolean =
        runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

    fun speechIntent(offlineOnly: Boolean, language: String = "zh-CN"): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            if (offlineOnly) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

    fun errorText(code: Int, offlineOnly: Boolean): String = when {
        offlineOnly && (code == SpeechRecognizer.ERROR_NETWORK ||
            code == SpeechRecognizer.ERROR_SERVER ||
            code == SpeechRecognizer.ERROR_NO_MATCH) ->
            "离线语音识别不可用：请在设置里下载中文离线语音包"
        code == SpeechRecognizer.ERROR_NO_MATCH -> "没有听清，请再说一遍"
        code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没检测到声音，请靠近麦克风再说"
        code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "未授权录音权限"
        code == SpeechRecognizer.ERROR_NETWORK || code == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "网络异常，语音识别失败"
        else -> "语音识别失败，请重试"
    }
}
