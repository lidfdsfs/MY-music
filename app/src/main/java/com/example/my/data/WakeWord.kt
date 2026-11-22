package com.example.my.data

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * 语音唤醒的词与开关。
 *
 * 唤醒词只存本机 SharedPreferences，不上传；默认「你好，小纸」，用户可自定义。
 */
object WakeWord {
    private const val PREF = "wake_word"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PHRASE = "phrase"

    const val DEFAULT_PHRASE = "你好，小纸"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun phrase(context: Context): String =
        prefs(context).getString(KEY_PHRASE, DEFAULT_PHRASE).orEmpty().ifBlank { DEFAULT_PHRASE }

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    fun setPhrase(context: Context, text: String) {
        prefs(context).edit().putString(KEY_PHRASE, text).apply()
    }

    /** 归一化：去掉空格与标点，只留文字/数字，方便「你 好 小 纸」与「你好小纸」比对 */
    fun normalize(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

    /**
     * 识别文本里是否出现唤醒词。
     *
     * 小模型对同音字常有偏差（小纸 / 晓纸），所以 4 字以上允许 1 个字的误差；
     * 只在比较这句短文本时做一次滑窗，开销可以忽略。
     */
    fun matches(text: String, phrase: String): Boolean {
        val t = normalize(text)
        val k = normalize(phrase)
        if (t.isBlank() || k.isBlank()) return false
        if (t.contains(k)) return true
        if (k.length < 4 || t.length < k.length) return false
        for (i in 0..(t.length - k.length)) {
            if (withinOneEdit(t.substring(i, i + k.length), k)) return true
        }
        return false
    }

    private fun withinOneEdit(a: String, b: String): Boolean {
        var diff = 0
        var i = 0
        while (i < a.length) {
            if (a[i] != b[i]) {
                diff++
                if (diff > 1) return false
            }
            i++
        }
        return true
    }
}

/**
 * 语音唤醒引擎：只在 App 前台运行，退后台立刻释放麦克风与识别器。
 *
 * 省电做法：
 *  - 用 Vosk 本地模型，不向云端持续推流（云端识别是唤醒场景最大的耗电来源）；
 *  - 系统省电模式 / 低电耗待机（Doze）时自动停掉麦克风，退出省电后自动恢复；
 *  - 熄屏（不可交互）时不监听；
 *  - 没有离线模型或没有录音权限时静默不启动，不空转；
 *  - 命中一次就停，回到 AI 页后不会重复触发。
 */
class WakeWordEngine(
    private val context: Context,
    private val onWake: () -> Unit
) {
    private var vosk: VoskRecognizer? = null
    private var running = false

    /** 上层是否希望监听（前台 + 开关打开 + 不在 AI 页）；省电模式解除后据此自动恢复 */
    private var desired = false

    /** 系统省电模式 / Doze 状态监听，用来在切到省电时立刻松手麦克风 */
    private var powerReceiver: BroadcastReceiver? = null

    /** 唤醒词随时可改：匹配时实时读取，不用重载模型 */
    @Volatile
    private var phrase: String = WakeWord.phrase(context)

    fun setPhrase(text: String) {
        if (WakeWord.normalize(text).isBlank()) return
        phrase = text
    }

    fun start() {
        desired = true
        startInternal()
    }

    private fun startInternal() {
        if (running) return
        // 上一轮如果停在错误/命中状态，模型还挂在旧实例上：先释放，避免反复重启时越积越多
        vosk?.release()
        vosk = null
        if (WakeWord.normalize(phrase).isBlank()) return
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        // 熄屏 / 省电模式 / 系统深度待机：这一轮直接不占用麦克风
        if (pm != null && !pm.isInteractive) return
        if (pm?.isPowerSaveMode == true) return
        if (pm?.isDeviceIdleMode == true) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val engine = VoskRecognizer(context)
        if (!engine.hasModel()) return
        running = true
        vosk = engine
        watchPowerState(pm)
        engine.init { ok ->
            if (!ok || !running) return@init
            engine.watchWakeWord(
                phraseOf = { phrase },
                onHit = {
                    running = false
                    onWake()
                },
                onError = { running = false }
            )
        }
    }

    /** 停掉麦克风，但保留「上层还想听」的意图（省电模式解除后能自己回来） */
    private fun stopListening() {
        running = false
        vosk?.stopWakeWatch()
        vosk?.release()
        vosk = null
    }

    fun stop() {
        desired = false
        stopListening()
        powerReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        powerReceiver = null
    }

    /**
     * 省电模式 / 深度待机是运行中才打开的：只听一次启动时的状态不够，
     * 必须盯着系统广播，一进省电立刻松手麦克风；退出省电再按上层意图恢复。
     */
    private fun watchPowerState(pm: PowerManager?) {
        if (pm == null || powerReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED,
                    PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                        val save = pm.isPowerSaveMode || pm.isDeviceIdleMode
                        if (save) {
                            stopListening()
                        } else if (desired) {
                            startInternal()
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        powerReceiver = receiver
    }
}
