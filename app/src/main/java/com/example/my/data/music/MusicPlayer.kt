package com.example.my.data.music

import android.content.ContentUris
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.my.data.WebSafety
import com.example.my.data.sync.MusicFavoritesSync
import com.example.my.ui.i18n.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 一首本地音乐 */data class LocalSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: Uri,
    /** 音频在共享存储中的路径；仅用于查找同目录同名 .lrc，可能为空 */
    val path: String = "",
    /** 未清洗的原始标题（文件名编号还在），用于按原名找同目录 .lrc */
    val rawTitle: String = "",
    /** 网页资源请求头（Referer / Cookie / UA）；本地歌曲为空 */
    val headers: Map<String, String> = emptyMap(),
    /**
     * 截图导入的「待获取」记录：只有歌名和歌手，还没有音源。
     * 点击播放时才去搜索、下载，成功后替换成真正的缓存歌曲。
     */
    val pendingSearch: Boolean = false
) {
    /** 封面缩略图 Uri（系统专辑封面） */
    fun albumArtUri(): Uri =
        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), id)
}

/**
 * 播放模式：列表循环（默认） / 单曲循环 / 随机 / 顺序播放（播完末尾停止）。
 *
 * 旧版只有 SEQUENCE / LOOP_ONE / SHUFFLE 三态，其中 SEQUENCE 实际上就是列表循环；
 * 升级时把存量的 "SEQUENCE" 读成 LOOP_ALL 保持原行为，新的 ORDER 专门表示
 * 「播到末尾不回头」。ORDER 用一个新名字，避免和旧偏好值混淆。
 */
enum class PlayMode { LOOP_ALL, LOOP_ONE, SHUFFLE, ORDER }

/** 当前队列来自哪个入口：播放列表弹层据此显示对应来源的歌曲 */
enum class QueueSource { LIBRARY, FAVORITES, RECENT, PLAYLIST }

/** 均衡器预设（自定义 5 段电平，单位 mB） */
enum class EqPreset(val levels: ShortArray) {
    OFF(shortArrayOf()),
    POP(shortArrayOf(300, 200, 0, 150, 250)),
    ROCK(shortArrayOf(500, 250, -100, 200, 450)),
    JAZZ(shortArrayOf(250, 150, 0, 200, 300)),
    CLASSIC(shortArrayOf(400, 100, 0, 250, 150)),
    BASS(shortArrayOf(800, 500, 150, 0, 0))
}

/**
 * 本地音乐播放器单例。
 *
 * - 扫描 MediaStore.Audio 获取本机歌曲；
 * - MediaPlayer 播放/暂停/切歌/拖动进度，支持播放模式、收藏、睡眠定时、均衡器、最近播放；
 * - 状态用 Compose 可观察状态暴露，UI 直接读；
 * - 任何状态变化统一经 [MusicExternal.notifyChanged] 同步系统通知、媒体会话与桌面小组件。
 *
 * 放在单例而不是 Service：App 本来就是纯本地、不联网、退出页面即停，避免后台服务常驻；
 * 通知采用普通高优先级通知 + MediaSession，用户仍能在通知中心/锁屏/耳机上看到并控制。
 */
object MusicPlayer {

    private const val PREFS = "music_prefs"
    private const val KEY_MODE = "play_mode"
    private const val KEY_FAV = "favorites"
    private const val KEY_EQ = "eq_preset"
    /** 音效库：当前音效 id（空 = 原声）、开关、调音台参数 */
    private const val KEY_SOUND_FX = "sound_fx_id"
    private const val KEY_SOUND_FX_ON = "sound_fx_on"
    private const val KEY_SOUND_FX_CUSTOM = "sound_fx_custom"
    private const val KEY_RECENT = "recent"
    private const val KEY_PLAY_COUNTS = "play_counts_v1"
    private const val KEY_CUSTOM = "custom_songs_v1"
    /** 截图导入但还没获取音源的占位记录（歌名/歌手） */
    private const val KEY_PENDING = "pending_songs_v1"
    /** 歌曲播放时是否允许被来电 / 导航 / 其他音乐等系统声音打断（默认允许） */
    private const val KEY_INTERRUPTIBLE = "allow_interruptions"
    /** 长按改名后的元数据覆盖（id → 歌名/歌手），重启仍生效 */
    private const val KEY_META = "song_meta_v1"
    /** 长按删除的系统媒体库歌曲 id：只从 App 列表隐藏，绝不删用户文件 */
    private const val KEY_HIDDEN = "hidden_songs_v1"
    /** 上次播放到哪首歌 / 哪个位置；重新打开播放器时接着显示，不从列表第一首开始 */
    private const val KEY_LAST_ID = "last_song_id_v1"
    private const val KEY_LAST_POS = "last_position_ms_v1"
    /** 听歌年报统计（总时长/按天/深夜/总次数）的 JSON 档 */
    private const val KEY_LISTEN_STATS = "listen_stats_v1"
    /** 音乐闹钟到点时间戳 */
    private const val KEY_ALARM_END = "alarm_end_ms"
    /** 摇一摇切歌 / 翻腕静音开关 */
    private const val KEY_SHAKE_NEXT = "shake_next_on"
    private const val KEY_FLIP_PAUSE = "flip_pause_on"
    /** 没有当前歌曲时的哨兵：网页歌曲 id 是负数，不能拿 -1 当空值 */
    private const val NO_SONG_ID = Long.MIN_VALUE

    var songs by mutableStateOf<List<LocalSong>>(emptyList())
        private set

    /** 用户手动导入并复制进私有目录的歌曲（跨重启保留） */
    private var customSongs: List<LocalSong> = emptyList()
    private var mediaSongs: List<LocalSong> = emptyList()
    private var metaOverrides: Map<Long, Pair<String, String>> = emptyMap()
    private var hiddenIds: Set<Long> = emptySet()

    /**
     * 「资源探」下载到本地缓存里的网页歌曲：文件在 `files/web_music/`，
     * 重启后从缓存目录重建，不进系统媒体库。
     */
    var webSongs by mutableStateOf<List<LocalSong>>(emptyList())
        private set

    /**
     * 截图导入的待获取歌曲：只存歌名/歌手，点击播放时才搜源下载。
     * 单独持久化，[refreshWebCache] 重建缓存时不会被覆盖掉。
     */
    private var pendingSongs: List<LocalSong> = emptyList()

    /** 正在为哪首歌获取音源；UI 可据此显示“正在获取…” */
    var resolvingSongId by mutableStateOf<Long?>(null)
        private set
    var resolvingSongTitle by mutableStateOf("")
        private set

    /** 当前点击进入的列表队列；为空时退回整个音乐库 */
    private var activeQueue: List<LocalSong>? = null

    /** 实际播放队列：用户点进哪个列表，就只在该列表内切歌 */
    private fun queue(): List<LocalSong> =
        activeQueue?.takeIf { it.isNotEmpty() } ?: (songs + webSongs)

    /** 播放列表弹层读取的当前队列（保持入口列表的顺序） */
    val currentQueue: List<LocalSong> get() = queue()

    /** 当前队列来源：由 playFrom 的调用方显式标记，弹层据此默认显示对应列表 */
    var queueSource by mutableStateOf(QueueSource.LIBRARY)
        private set

    /** 下载完成：入队并立刻播放；同时移除同 id 的待获取占位记录 */
    fun addCachedWebSong(song: LocalSong) {
        pendingSongs = pendingSongs.filterNot { it.id == song.id }
        persistPending()
        webSongs = pendingSongs +
            (webSongs.filterNot { it.id == song.id || it.pendingSearch } + applyMeta(song)).takeLast(50)
    }

    /** 重新扫描缓存目录；待获取的占位记录始终排在前面 */
    fun refreshWebCache(context: Context) {
        webSongs = pendingSongs + WebMusicCache.list(context).map { applyMeta(it) }
    }

    /** 清空网页歌曲缓存，返回删掉的文件数；待获取记录保留 */
    fun clearWebCache(context: Context): Int {
        val n = WebMusicCache.clear(context)
        webSongs = pendingSongs
        return n
    }

    /** 截图导入：只记录歌名/歌手，音源在用户点击播放时再获取。 */
    suspend fun addPendingSongs(items: List<LocalSong>) = withContext(Dispatchers.Main.immediate) {
        if (items.isEmpty()) return@withContext
        val merged = (pendingSongs + items).distinctBy { it.id }
        pendingSongs = merged
        webSongs = (merged + webSongs.filterNot { it.pendingSearch }).distinctBy { it.id }
        persistPending()
        contextApp?.let { MusicExternal.notifyChanged(it) }
    }

    private fun persistPending() {
        val arr = JSONArray()
        pendingSongs.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("title", s.title)
                    .put("artist", s.artist)
                    .put("album", s.album)
            )
        }
        contextApp?.let {
            it.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_PENDING, arr.toString())
                .apply()
        }
    }

    private fun decodePending(raw: String): List<LocalSong> = runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optString("title").trim()
            if (title.isBlank()) return@mapNotNull null
            LocalSong(
                id = o.optLong("id"),
                title = title,
                artist = o.optString("artist").trim(),
                album = o.optString("album").ifBlank { "截图导入" },
                durationMs = 0L,
                uri = Uri.EMPTY,
                pendingSearch = true
            )
        }
    }.getOrDefault(emptyList())

    var current by mutableStateOf<LocalSong?>(null)
        private set

    var isPlaying by mutableStateOf(false)

    /** 播放进度 0~1，UI 进度条绑这个 */
    var progress by mutableFloatStateOf(0f)

    var positionMs by mutableLongStateOf(0L)

    var durationMs by mutableLongStateOf(0L)

    var mode by mutableStateOf(PlayMode.LOOP_ALL)
        private set

    var eqPreset by mutableStateOf(EqPreset.OFF)
        private set

    /**
     * 是否允许系统其他声音（来电、导航、别的播放器）打断当前歌曲。
     *
     * true：沿用系统音频焦点，短暂丢失时暂停、焦点回来后续播；
     * false：收到焦点丢失回调也不暂停，音乐继续播（可能与系统其他声音同时出声）。
     */
    var interruptible by mutableStateOf(true)
        private set

    /** 设置是否允许被打断并持久化；正在播放时立即生效 */
    fun setInterruptionsAllowed(allow: Boolean) {
        interruptible = allow
        if (!allow) resumeAfterFocusGain = false
        contextApp?.let { prefs().edit().putBoolean(KEY_INTERRUPTIBLE, allow).apply() }
    }

    /** 睡眠定时结束时间戳，0 = 关闭 */
    var sleepEndMs by mutableLongStateOf(0L)
        private set

    /** 睡眠定时「播完当前歌再停」模式：不管还剩多久，这首歌播完就停 */
    var sleepAfterCurrent by mutableStateOf(false)
        private set

    /**
     * 插队队列（下一首播放）：下一次切歌优先从这里取。
     * 等待期间不动播放队列；真正轮到它时 consumeUpNext() 会把它挪到当前歌之后，
     * 于是「当前歌 → 插队歌 → 原来的下一首」在队列里也看得见、拖得动。
     */
    var upNext by mutableStateOf<List<LocalSong>>(emptyList())
        private set

    /** 插队：把歌放到「下一首」。已在队里就挪到队尾，最多记 20 首 */
    fun playNext(song: LocalSong) {
        // 待获取的歌还没有音源，插队了也播不了
        if (song.pendingSearch) return
        upNext = (upNext.filterNot { it.id == song.id } + song).take(20)
        contextApp?.let { MusicExternal.notifyChanged(it) }
    }

    fun removeUpNext(id: Long) {
        upNext = upNext.filterNot { it.id == id }
    }

    fun clearUpNext() {
        if (upNext.isEmpty()) return
        upNext = emptyList()
        contextApp?.let { MusicExternal.notifyChanged(it) }
    }

    /**
     * 消费一条「下一首播放」：把队首插到当前歌之后，队列就是真实播放顺序。
     * 这样插队歌播完会顺着队列回到原来的下一首，而不是被追加到队尾后跳回第一首；
     * 按「上一首」也能回到被打断的那首歌。返回 false 表示没有插队项，交给常规切歌。
     */
    private fun consumeUpNext(): Boolean {
        var song = upNext.firstOrNull() ?: return false
        // 占位歌曲没有音源，跳过它，不让队列卡住
        while (song.pendingSearch) {
            upNext = upNext.drop(1)
            song = upNext.firstOrNull() ?: return false
        }
        upNext = upNext.drop(1)
        val base = (activeQueue ?: queue()).toMutableList()
        val anchor = base.indexOfFirst { it.id == current?.id }
        val insertAt = if (anchor >= 0) anchor + 1 else base.size
        val existing = base.indexOfFirst { it.id == song.id }
        if (existing >= 0) {
            // 已在队列里：移动到当前歌之后（先删后插要修正下标），避免同一首出现两次
            base.removeAt(existing)
            val target = if (existing < insertAt) insertAt - 1 else insertAt
            base.add(target.coerceIn(0, base.size), song)
        } else {
            base.add(insertAt.coerceIn(0, base.size), song)
        }
        playFrom(base, song)
        return true
    }

    /** 从当前播放队列移除一首；不动音乐库文件。队列还没实体化时先复制一份再删 */
    fun removeFromQueue(id: Long): Boolean {
        val base = activeQueue ?: queue()
        if (base.size <= 1 || base.none { it.id == id }) return false
        activeQueue = base.filterNot { it.id == id }
        // 移出后不该在「即将播放」里再冒出来
        upNext = upNext.filterNot { it.id == id }
        // 移除的正好是正在播的歌：顺势切到下一首，不让播放器停在空队列里
        if (current?.id == id) {
            next()
        } else {
            contextApp?.let { MusicExternal.notifyChanged(it) }
        }
        return true
    }

    /** 队列内上移 / 下移一位（delta -1 / +1）；同样只影响播放顺序 */
    fun moveInQueue(id: Long, delta: Int): Boolean {
        if (delta == 0) return false
        val base = (activeQueue ?: queue()).toMutableList()
        val idx = base.indexOfFirst { it.id == id }
        if (idx < 0) return false
        val to = (idx + delta).coerceIn(0, base.size - 1)
        if (to == idx) return false
        base.add(to, base.removeAt(idx))
        activeQueue = base
        contextApp?.let { MusicExternal.notifyChanged(it) }
        return true
    }

    /**
     * 队列拖拽排序落盘：必须给出当前队列的完整 id 序列。
     * 搜索过滤时提交会把没显示的歌丢掉，所以长度 / 去重长度必须都等于队列长度；
     * 顺序没变就不写，避免无意义的通知刷新。
     */
    fun setQueueOrder(orderedIds: List<Long>): Boolean {
        val base = activeQueue ?: queue()
        if (base.isEmpty()) return false
        if (orderedIds.size != base.size || orderedIds.distinct().size != base.size) return false
        val byId = base.associateBy { it.id }
        val reordered = orderedIds.mapNotNull { byId[it] }
        if (reordered.size != base.size) return false
        if (reordered.map { it.id } == base.map { it.id }) return false
        activeQueue = reordered
        contextApp?.let { MusicExternal.notifyChanged(it) }
        return true
    }

    /** 播放倍速：1.0 = 原速；换歌后保持当前倍速 */
    var playbackSpeed by mutableFloatStateOf(1f)
        private set

    var favorites by mutableStateOf<Set<Long>>(emptySet())
        private set

    var recentIds by mutableStateOf<List<Long>>(emptyList())
        private set

    /** 每首歌的累计播放次数：只统计本机主动开播，用于「我的-听歌报告」 */
    var playCounts by mutableStateOf<Map<Long, Int>>(emptyMap())
        private set

    // ---------- 听歌年报统计：时长 / 按天 / 深夜 / 总次数 ----------

    /** 累计收听毫秒数：进度循环里逐 tick 累加，暂停不计 */
    var listenTotalMs by mutableStateOf(0L)
        private set

    /** 按天（yyyy-MM-dd）收听毫秒数：连听天数、最多的一天都从这里算 */
    var listenByDayMs by mutableStateOf<Map<String, Long>>(emptyMap())
        private set

    /** 深夜（23 点–6 点）主动开播次数 */
    var listenNightPlays by mutableStateOf(0)
        private set

    /** 主动开播总次数 */
    var listenTotalPlays by mutableStateOf(0)
        private set

    /** 统计是否被用户清空过：年报页据此区分「还没听」和「没数据」 */
    var listenHasData by mutableStateOf(false)
        private set

    /** 按小时（0–23）主动开播次数：爱听时段折线图用 */
    var listenByHour by mutableStateOf<Map<Int, Int>>(emptyMap())
        private set

    /** 按小时归档的最近开播歌曲 id（去重保序，每小时限 20 首）：时段歌单用 */
    var listenHourSongs by mutableStateOf<Map<Int, List<Long>>>(emptyMap())
        private set

    /** 按天（yyyy-MM-dd）归档的开播歌曲 id（去重保序，保留最近 60 天）：听歌日历 / 区间歌单用 */
    var listenByDaySongs by mutableStateOf<Map<String, List<Long>>>(emptyMap())
        private set

    /** 听歌统计攒了多久没落盘；进度循环里每 30 秒写一次，暂停/退出立刻写 */
    private var statsDirtyAt = 0L

    /** 音乐闹钟：到点时间戳，0 = 未设置。进程存活才会响，重启/被杀会丢（UI 有说明） */
    var alarmEndMs by mutableStateOf(0L)
        private set

    /** 闹钟淡入开始时间：起播那一刻置为 now，10 秒内音量从 0 线性升到 1 */
    private var alarmRampStartMs = 0L

    /** 摇一摇切歌开关 */
    var shakeNextOn by mutableStateOf(false)
        private set

    /** 翻腕静音（屏幕朝下 1.5 秒自动暂停）开关 */
    var flipPauseOn by mutableStateOf(false)
        private set

    private var sensorManager: android.hardware.SensorManager? = null
    private var sensorRegistered = false

    /** 最近一次错误，供 UI 提示（例如文件损坏） */
    var lastError by mutableStateOf<String?>(null)
        private set

    private var player: MediaPlayer? = null
    /** 音频会话 id：Visualizer（闪光灯跟节奏闪）要挂在这个会话上；没在播就是 0 */
    val audioSessionId: Int get() = runCatching { player?.audioSessionId ?: 0 }.getOrDefault(0)
    /** MediaPlayer 已 prepared；未 prepared 时 start() 会抛 IllegalStateException */
    private var prepared = false
    /** prepared 之后是否还要自动开播：用户在准备阶段按暂停会把它置为 false */
    private var playWhenPrepared = false
    /** 每次 playInternal 递增，用来丢弃旧播放器迟到的 completion / error 回调 */
    private var playGeneration = 0
    /** 连续播放失败计数：全部歌曲都失败时停下来，避免无限跳歌 */
    private var consecutiveErrors = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var progressJob: Job? = null
    /** prepareAsync 长时间不回调的兜底：避免某一首卡在准备阶段导致整条队列停住 */
    private var prepareWatchdog: Job? = null
    /** 进度的唯一 ticker 复用同一个 scope，不再每次切歌都新建一个 CoroutineScope */
    private val tickerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** 上次把播放进度写入 SharedPreferences 的时间；每 4 秒最多落盘一次，避免频繁写文件 */
    private var lastPersistAt = 0L
    /** 待获取歌曲的搜源/下载不占用主线程 */
    private val resolveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var resolveJob: Job? = null
    private var equalizer: Equalizer? = null
    /** 音效库的其他四路 AudioEffect：低频 / 空间 / 混响 / 响度 */
    private var bassBoost: BassBoost? = null
    private var spatializer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null
    private var loudnessFx: LoudnessEnhancer? = null

    /** 当前音效 id（null 或关掉开关时视为原声） */
    var soundFxId by mutableStateOf<String?>(null)
        private set
    var soundFxOn by mutableStateOf(false)
        private set
    private var contextApp: Context? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    /** 因来电/导航等短暂打断而暂停时，拿到音频焦点后自动续播 */
    private var resumeAfterFocusGain = false

    /** 结尾自动淡出的窗口长度 */
    private const val FADE_OUT_MS = 3_000L
    /** 音乐闹钟起播音量淡入时长 */
    private const val ALARM_RAMP_MS = 10_000L

    /** 淡出是否已经生效：拖回前段 / 切歌后要把音量恢复满 */
    private var fadeActive = false

    /** 拔耳机 / 断蓝牙（ACTION_AUDIO_BECOMING_NOISY）：立刻暂停，不自动续播 */
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            resumeAfterFocusGain = false
            pauseFromFocusLoss()
        }
    }
    private var noisyRegistered = false

    /**
     * 系统音频焦点：其他应用播放音乐、来电、导航播报时让出音频通道。
     * 短暂丢失暂停并在焦点回来后恢复；永久丢失（别的播放器接管）不自动恢复。
     */
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        // 用户选了「不允许被打断」：焦点丢失只当作通知，不暂停当前歌曲。
        // Android 没有真正的独占音频 API；这里保证本 App 不主动让路，
        // 其他应用若坚持播放，系统可能混音而不是把我们静音。
        if (!interruptible) return@OnAudioFocusChangeListener
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeAfterFocusGain = false
                pauseFromFocusLoss()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                resumeAfterFocusGain = isPlaying
                pauseFromFocusLoss()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeAfterFocusGain && current != null && !isPlaying) {
                    resumeAfterFocusGain = false
                    when {
                        // 播放器丢了：重新从当前这首起播，并从暂停的位置继续
                        player == null -> current?.let {
                            playFrom(queue(), it, startPositionMs = positionMs)
                        }
                        // 焦点丢失时还在 prepareAsync：不能直接 start，标记好等 prepared 回调
                        !prepared -> {
                            playWhenPrepared = true
                            isPlaying = true
                            contextApp?.let { MusicExternal.notifyChanged(it) }
                        }
                        else -> player?.let { p ->
                            runCatching { p.start() }
                                .onSuccess {
                                    isPlaying = true
                                    startProgressLoop()
                                    contextApp?.let { MusicExternal.notifyChanged(it) }
                                }
                        }
                    }
                }
            }
        }
    }

    fun init(context: Context) {
        contextApp = context.applicationContext
        audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        // 拔耳机 / 断蓝牙自动暂停：注册一次就够（单例与 App 同生命周期）
        if (!noisyRegistered) {
            noisyRegistered = true
            runCatching {
                androidx.core.content.ContextCompat.registerReceiver(
                    context.applicationContext,
                    noisyReceiver,
                    IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                    androidx.core.content.ContextCompat.RECEIVER_EXPORTED
                )
            }
        }
        val p = prefs()
        // 旧偏好里的 "SEQUENCE" 语义就是列表循环；新版不会再写入这个名字，
        // 所以这条映射只作用于升级用户，不会把手动切到 ORDER 的人又改回去。
        mode = runCatching {
            when (val saved = p.getString(KEY_MODE, null)) {
                null, "SEQUENCE" -> PlayMode.LOOP_ALL
                else -> PlayMode.valueOf(saved)
            }
        }.getOrDefault(PlayMode.LOOP_ALL)
        eqPreset = runCatching { EqPreset.valueOf(p.getString(KEY_EQ, "OFF") ?: "OFF") }
            .getOrDefault(EqPreset.OFF)
        // 音效库的选中项 / 开关也要恢复，进播放页就是上次那套声音
        soundFxId = p.getString(KEY_SOUND_FX, null)
        soundFxOn = p.getBoolean(KEY_SOUND_FX_ON, false)
        interruptible = p.getBoolean(KEY_INTERRUPTIBLE, true)
        favorites = p.getString(KEY_FAV, "").orEmpty()
            .split(',').mapNotNull { it.toLongOrNull() }.toSet()
        recentIds = p.getString(KEY_RECENT, "").orEmpty()
            .split(',').mapNotNull { it.toLongOrNull() }.take(30)
        playCounts = p.getString(KEY_PLAY_COUNTS, "").orEmpty()
            .split(',').mapNotNull { token ->
                val parts = token.split(':')
                val id = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                val count = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                if (count <= 0) null else id to count
            }.toMap()
        // 老版本只有「最近播放」没有次数：把已知历史按每首 1 次补齐，
        // 避免升级后听歌报告全是 0；后续播放会继续累加。
        if (playCounts.isEmpty() && recentIds.isNotEmpty()) {
            playCounts = recentIds.associateWith { 1 }
            p.edit().putString(
                KEY_PLAY_COUNTS,
                playCounts.entries.joinToString(",") { "${it.key}:${it.value}" }
            ).apply()
        }
        customSongs = decodeCustom(p.getString(KEY_CUSTOM, "").orEmpty())
        metaOverrides = decodeMeta(p.getString(KEY_META, "").orEmpty())
        hiddenIds = p.getString(KEY_HIDDEN, "").orEmpty()
            .split(',').mapNotNull { it.toLongOrNull() }.toSet()
        pendingSongs = decodePending(p.getString(KEY_PENDING, "").orEmpty())
        // 网页歌曲缓存：下载过的歌重启后还在（文件在 files/web_music，设置里可清）
        webSongs = pendingSongs + WebMusicCache.list(context).map { applyMeta(it) }
        // 听歌年报统计 / 音乐闹钟 / 摇一摇、翻腕开关
        decodeListenStats(p.getString(KEY_LISTEN_STATS, "").orEmpty())
        alarmEndMs = p.getLong(KEY_ALARM_END, 0L)
        if (alarmEndMs > 0 && alarmEndMs <= System.currentTimeMillis()) {
            // 进程死掉期间错过的闹钟不补响，直接清掉，别让 UI 一直挂着「即将响铃」
            alarmEndMs = 0L
            p.edit().remove(KEY_ALARM_END).apply()
        }
        shakeNextOn = p.getBoolean(KEY_SHAKE_NEXT, false)
        flipPauseOn = p.getBoolean(KEY_FLIP_PAUSE, false)
        updateSensorListener()
        // 权限没授予时也要能看到自己导入过的歌（改名/隐藏在这里统一生效）
        if (songs.isEmpty()) rebuild()
    }

    private fun prefs() = contextApp!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 媒体库 + 手动导入的歌曲合并，稳定顺序：先导入的、后扫描到的 */
    private fun rebuild() {
        songs = (customSongs + mediaSongs)
            .distinctBy { it.id }
            .filter { it.id !in hiddenIds }
            .map { applyMeta(it) }
        activeQueue = null
        // 冷启动时 songs 为空才 rebuild；此时把上次播放的歌找回来。
        // 已经有当前歌曲（用户刚点了歌）时不动它，避免扫描完成把正在播的歌顶掉。
        if (current == null) restoreLastPlayed()
    }

    /**
     * 恢复上次播放的歌曲与播放位置。
     *
     * 只恢复“显示和进度”，不自动出声：用户回到播放器时看到的是上次那首歌，
     * 点播放再从上次的位置继续，而不是从列表第一首 / 歌曲 0:00 重新开始。
     */
    private fun restoreLastPlayed() {
        val ctx = contextApp ?: return
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedId = p.getLong(KEY_LAST_ID, NO_SONG_ID)
        val id = savedId.takeIf { it != NO_SONG_ID } ?: recentIds.firstOrNull() ?: return
        val song = queue().firstOrNull { it.id == id } ?: return
        val savedPos = if (savedId != NO_SONG_ID) p.getLong(KEY_LAST_POS, 0L) else 0L
        val pos = clampResumePosition(savedPos, song.durationMs)
        current = song
        positionMs = pos
        durationMs = song.durationMs
        progress = if (song.durationMs > 0L) pos.toFloat() / song.durationMs.toFloat() else 0f
        isPlaying = false
    }

    /** 记录当前歌曲和播放位置；暂停、退出、拖动进度和每 4 秒播放 tick 都会调用 */
    private fun persistLastPlayed() {
        val song = current ?: return
        val ctx = contextApp ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_ID, song.id)
            .putLong(KEY_LAST_POS, positionMs.coerceAtLeast(0L))
            .apply()
        lastPersistAt = SystemClock.elapsedRealtime()
    }

    /** App 退到后台时补一次精确落盘；未初始化或还没播过歌时什么也不做 */
    fun savePlaybackState() {
        persistLastPlayed()
    }

    /** 长按改名：覆盖歌名/歌手，扫描重建后依然保留 */
    private fun applyMeta(s: LocalSong): LocalSong {
        val ov = metaOverrides[s.id]
        if (ov != null) {
            return s.copy(
                title = ov.first.ifBlank { s.title },
                artist = ov.second.ifBlank { s.artist }
            )
        }
        // 历史反数据自愈：用户手动改过的一律尊重，只修「歌名是常见歌手」的高置信度情况
        val fixed = SongNameParser.repair(s.title, s.artist) ?: return s
        return s.copy(title = fixed.first, artist = fixed.second)
    }

    private fun decodeMeta(json: String): Map<Long, Pair<String, String>> = runCatching {
        if (json.isBlank()) return@runCatching emptyMap()
        val o = JSONObject(json)
        val names = o.names() ?: return@runCatching emptyMap()
        (0 until names.length()).mapNotNull { i ->
            val k = names.optString(i)
            val id = k.toLongOrNull() ?: return@mapNotNull null
            val v = o.optJSONObject(k) ?: return@mapNotNull null
            id to (v.optString("t", "") to v.optString("a", ""))
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun persistMeta() {
        val o = JSONObject()
        metaOverrides.forEach { (id, pair) ->
            o.put(id.toString(), JSONObject().put("t", pair.first).put("a", pair.second))
        }
        prefs().edit().putString(KEY_META, o.toString()).apply()
    }

    private fun persistHidden() {
        prefs().edit().putString(KEY_HIDDEN, hiddenIds.joinToString(",")).apply()
    }

    private fun persistCustom() {
        prefs().edit().putString(KEY_CUSTOM, encodeCustom(customSongs)).apply()
    }

    private fun encodeCustom(list: List<LocalSong>): String {
        val arr = JSONArray()
        for (s in list) {
            if (!"file".equals(s.uri.scheme, ignoreCase = true)) continue
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("title", s.title)
                    .put("raw", s.rawTitle)
                    .put("artist", s.artist)
                    .put("duration", s.durationMs)
                    .put("path", s.uri.path.orEmpty())
            )
        }
        return arr.toString()
    }

    private fun decodeCustom(json: String): List<LocalSong> = runCatching {
        if (json.isBlank()) return@runCatching emptyList()
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val file = File(o.optString("path", ""))
            if (!file.isFile) return@mapNotNull null
            LocalSong(
                id = o.optLong("id"),
                title = o.optString("title", file.nameWithoutExtension),
                rawTitle = o.optString("raw", file.name),
                artist = o.optString("artist", "未知艺术家"),
                album = "",
                durationMs = o.optLong("duration", 0L),
                uri = Uri.fromFile(file),
                path = file.absolutePath
            )
        }
    }.getOrDefault(emptyList())

    /** 重新扫描本机音频（需要 READ_MEDIA_AUDIO 权限已授予），返回合并后的歌曲总数 */
    fun scan(context: Context): Int {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )
        runCatching {
            resolver.query(
                collection, projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 30000",
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { c ->
                val list = ArrayList<LocalSong>(c.count)
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val rawTitle = c.getString(titleCol).orEmpty().ifBlank { "未知标题" }
                    val rawArtist = c.getString(artistCol)
                    // 歌名识别：1231红尘客栈 → 红尘客栈，"周杰伦 - 红尘客栈" 顺带拆出歌手
                    val parsed = SongNameParser.parse(rawTitle, rawArtist)
                    list.add(
                        LocalSong(
                            id = id,
                            title = parsed.title.ifBlank { rawTitle },
                            artist = parsed.artist ?: "未知艺术家",
                            album = c.getString(albumCol) ?: "",
                            durationMs = c.getLong(durCol),
                            uri = ContentUris.withAppendedId(collection, id),
                            path = if (dataCol >= 0) c.getString(dataCol).orEmpty() else "",
                            rawTitle = rawTitle
                        )
                    )
                }
                mediaSongs = list
                reconcilePendingWithMedia()
                rebuild()
            }
        }
        return songs.size
    }

    /**
     * 云端恢复出来的「待获取」占位，一旦媒体库扫描到同名同歌手歌曲，
     * 就把收藏里的占位 id 换成真实歌曲 id，避免列表里出现两条同样的歌。
     */
    private fun reconcilePendingWithMedia() {
        if (pendingSongs.isEmpty() || mediaSongs.isEmpty()) return
        val byKey = mediaSongs.associateBy { MusicFavoritesSync.keyOf(it.title, it.artist) }
        val remap = LinkedHashMap<Long, Long>()
        val remaining = ArrayList<LocalSong>(pendingSongs.size)
        pendingSongs.forEach { pending ->
            val hit = byKey[MusicFavoritesSync.keyOf(pending.title, pending.artist)]
            if (hit != null) remap[pending.id] = hit.id else remaining.add(pending)
        }
        if (remap.isEmpty()) return
        favorites = favorites.map { remap[it] ?: it }.toSet()
        pendingSongs = remaining
        webSongs = remaining + webSongs.filterNot { it.pendingSearch }
        prefs().edit().putString(KEY_FAV, favorites.joinToString(",")).apply()
        persistPending()
    }

    fun play(song: LocalSong) {
        playFrom(songs + webSongs, song, QueueSource.LIBRARY)
    }

    /** 从指定列表开始播放：上一首 / 下一首只在这个列表内循环 */
    fun playFrom(
        list: List<LocalSong>,
        song: LocalSong,
        source: QueueSource? = null,
        startPositionMs: Long = 0L
    ) {
        val base = list.ifEmpty { listOf(song) }.distinctBy { it.id }
        activeQueue = if (base.any { it.id == song.id }) base else base + song
        // 手动点播了「即将播放」里的歌：从插队队列里撤掉，避免之后又播一遍
        if (upNext.any { it.id == song.id }) upNext = upNext.filterNot { it.id == song.id }
        // 切歌/上一首/下一首沿用原来源；只有从具体入口点歌时才重新标记
        source?.let { queueSource = it }
        if (song.pendingSearch) {
            resolveThenPlay(song)
        } else {
            resolveJob?.cancel()
            resolvingSongId = null
            playInternal(song, startPositionMs)
        }
    }

    /**
     * 截图导入的占位歌曲：点击播放时才搜源、下载，成功后替换占位记录并播放。
     * 失败只报一次错，不自动跳到别的歌，避免队列里其它待获取记录连锁触发网络请求。
     */
    private fun resolveThenPlay(song: LocalSong) {
        if (resolvingSongId == song.id) return
        val ctx = contextApp ?: return
        resolveJob?.cancel()
        resolvingSongId = song.id
        resolvingSongTitle = song.title
        lastError = null
        resolveJob = resolveScope.launch {
            val resolved = try {
                PlaylistShotImporter.resolveSong(ctx, song.title, song.artist)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                null
            }
            withContext(Dispatchers.Main.immediate) {
                resolvingSongId = null
                if (resolved == null) {
                    lastError = "没找到「${song.title}」的音源，稍后再试"
                    MusicExternal.notifyChanged(ctx)
                    return@withContext
                }
                pendingSongs = pendingSongs.filterNot { it.id == song.id }
                persistPending()
                // 占位记录在收藏里，替换后保留收藏状态
                markFavorite(resolved.id)
                addCachedWebSong(resolved)
                activeQueue = activeQueue?.map { if (it.id == song.id) resolved else it }
                    ?: listOf(resolved)
                playInternal(resolved)
            }
        }
    }

    private fun playInternal(song: LocalSong, startPositionMs: Long = 0L) {
        val ctx = contextApp ?: return
        // 单音源：音乐页一起播，足迹配乐立刻让路，避免同一首歌两层声音
        BgmPlayback.pause()
        // 连续切歌时保留音频焦点，避免 release 后到 prepare 完成之间被别的应用抢走
        releaseInternal(abandonFocus = false)
        lastError = null
        prepared = false
        playWhenPrepared = true
        val generation = ++playGeneration
        val resumeAt = clampResumePosition(startPositionMs, song.durationMs)
        player = try {
            MediaPlayer().apply {
                // 私有目录里导入的音频用文件路径，避免部分 ROM 对 file:// Uri 的限制
                val scheme = song.uri.scheme?.lowercase()
                when {
                    scheme == "file" && song.path.isNotBlank() -> setDataSource(song.path)
                    // 网页资源：带请求头流式播（Referer / Cookie 过防盗链），不下载整首
                    scheme == "http" || scheme == "https" -> setDataSource(
                        HttpMediaSource(song.uri.toString(), song.headers)
                    )
                    else -> setDataSource(ctx, song.uri)
                }
                setOnPreparedListener { mp ->
                    if (generation != playGeneration) return@setOnPreparedListener
                    prepareWatchdog?.cancel()
                    prepareWatchdog = null
                    prepared = true
                    // 先定位到恢复位置，再判断是否自动开播：
                    // 即使用户在准备阶段按了暂停，后面再点播放也不会退回 0:00。
                    if (resumeAt > 0L) runCatching { mp.seekTo(resumeAt.toInt()) }
                    // 用户在准备阶段按了暂停：保持暂停，不要再自动开播
                    if (!playWhenPrepared) return@setOnPreparedListener
                    // 音频焦点只决定「被打断时让不让路」，不能决定「能不能播」。
                    // 部分 ROM / 其他 App 长期占着焦点时，requestAudioFocus 会返回失败；
                    // 以前这里直接 return，用户点播放/下一首就永远没声音。
                    requestAudioFocus()
                    runCatching { mp.start() }
                        .onSuccess {
                            consecutiveErrors = 0
                            this@MusicPlayer.isPlaying = true
                            // 网页音频一开始不知道时长，prepared 之后用解码出来的
                            MusicPlayer.durationMs =
                                if (song.durationMs > 0) song.durationMs else mp.duration.toLong()
                            applySpeed(mp)
                            restoreEffects()
                            startProgressLoop()
                            MusicExternal.notifyChanged(ctx)
                        }
                        .onFailure {
                            lastError = "播放失败"
                            this@MusicPlayer.isPlaying = false
                            prepared = false
                            playWhenPrepared = false
                            resumeAfterFocusGain = false
                            abandonAudioFocus()
                            MusicExternal.notifyChanged(ctx)
                            // 起播就失败：跳到下一首，不停在一个没声音的播放器上
                            trySkipBrokenSong(generation)
                        }
                }
                setOnCompletionListener {
                    if (generation == playGeneration) onCompleted(generation)
                }
                setOnErrorListener { _, what, _ ->
                    if (generation == playGeneration) {
                        prepareWatchdog?.cancel()
                        prepareWatchdog = null
                        prepared = false
                        playWhenPrepared = false
                        this@MusicPlayer.isPlaying = false
                        lastError = "播放失败($what)"
                        MusicExternal.notifyChanged(ctx)
                        trySkipBrokenSong(generation)
                    }
                    true
                }
                prepareAsync()
                armPrepareWatchdog(generation)
            }
        } catch (e: Exception) {
            prepareWatchdog?.cancel()
            prepareWatchdog = null
            lastError = "无法打开这个音频"
            isPlaying = false
            prepared = false
            playWhenPrepared = false
            resumeAfterFocusGain = false
            abandonAudioFocus()
            // 先记成当前这首歌，自动跳过时才知道该从它往后找，
            // 否则会一直重复打开同一首坏文件。
            current = song
            MusicExternal.notifyChanged(ctx)
            trySkipBrokenSong(generation)
            null
        }
        current = song
        progress = if (song.durationMs > 0L) resumeAt.toFloat() / song.durationMs.toFloat() else 0f
        positionMs = resumeAt
        durationMs = song.durationMs
        // 进入准备阶段就按「播放中」展示，通知栏不会在切歌瞬间显示一个点了没反应的播放键
        isPlaying = player != null
        persistLastPlayed()
        pushRecent(song.id)
        MusicExternal.notifyChanged(ctx)
    }

    /**
     * 播放「资源探」捞到的网页音频：流式播放，不下载到手机。
     * 地址仍要过 WebSafety 闸门，非 http/https 一律不播。
     */
    fun playNetwork(rawTitle: String, url: String, referer: String = "", cookie: String = "", userAgent: String = ""): Boolean {
        val safe = WebSafety.sanitize(url) ?: return false
        val info = SongNameParser.parse(rawTitle)
        val headers = buildMap {
            if (referer.isNotBlank()) put("Referer", referer)
            if (cookie.isNotBlank()) put("Cookie", cookie)
            if (userAgent.isNotBlank()) put("User-Agent", userAgent)
        }
        val song = LocalSong(
            id = (safe.hashCode().toLong() and 0x7FFFFFFFL) + 1L,
            title = info.title.ifBlank { "网页音频" },
            artist = info.artist.orEmpty(),
            album = "网页资源",
            durationMs = 0L,
            uri = Uri.parse(safe),
            rawTitle = rawTitle,
            headers = headers
        )
        // 同一条链接只留一首；只留最近 20 首，避免队列无限长
        webSongs = (webSongs.filterNot { it.id == song.id } + song).takeLast(20)
        play(song)
        return true
    }

    /**
     * 某些 ROM / 失效网页音源会「不回调 prepared 也不回调 error」，
     * 25 秒还没准备好就当作这一首坏了，自动跳下一首。
     * 用户中途暂停时不计时，避免暂停后还被自动切歌。
     */
    private fun armPrepareWatchdog(generation: Int) {
        prepareWatchdog?.cancel()
        prepareWatchdog = tickerScope.launch {
            delay(25_000)
            if (generation == playGeneration && !prepared &&
                player != null && playWhenPrepared && isPlaying
            ) {
                lastError = "准备超时，自动切换下一首"
                isPlaying = false
                playWhenPrepared = false
                trySkipBrokenSong(generation)
            }
        }
    }

    /**
     * 某首歌打不开 / 解码失败 / 起播失败时自动跳下一首。
     *
     * 只有整个队列都失败时才停：consecutiveErrors 在某首歌成功起播时清零，
     * 所以坏文件、失效的网页音源不会把整个播放卡死。
     */
    private fun trySkipBrokenSong(generation: Int) {
        consecutiveErrors++
        val list = queue()
        if (list.size > 1 && consecutiveErrors < list.size) {
            // 不在 MediaPlayer 回调栈里同步 release 旧实例，交给主线程队列
            mainHandler.post {
                if (generation == playGeneration) next()
            }
        } else {
            // 全部失败才停下，别让音频焦点一直被一个没声音的播放器占着
            isPlaying = false
            resumeAfterFocusGain = false
            abandonAudioFocus()
            contextApp?.let { MusicExternal.notifyChanged(it) }
        }
    }

    private fun onCompleted(generation: Int) {
        if (generation != playGeneration) return
        // 不要在 MediaPlayer 的 completion 回调里同步 release 旧实例：
        // 部分 ROM 会在回调栈里 stop/release 时卡住或抛异常，表现为「播完不切歌」。
        isPlaying = false
        playWhenPrepared = false
        progressJob?.cancel()
        val ctx = contextApp ?: return
        mainHandler.post {
            if (generation != playGeneration) return@post
            // 「播完当前歌再停」：这一首到头就停，不切下一首
            if (sleepAfterCurrent) {
                sleepAfterCurrent = false
                persistLastPlayed()
                MusicExternal.notifyChanged(ctx)
                return@post
            }
            // 「下一首播放」是用户显式指定的顺序，优先级高于播放模式：
            // 单曲循环、顺序播放到末尾，也必须先把插队歌唱完。
            // 睡眠定时「播完当前就停」在上面已经先返回，这里不覆盖它。
            if (upNext.isNotEmpty()) {
                next()
                MusicExternal.notifyChanged(ctx)
                return@post
            }
            val list = queue()
            val idx = current?.let { c -> list.indexOfFirst { s -> s.id == c.id } } ?: -1
            when (mode) {
                PlayMode.LOOP_ONE -> {
                    // current 为空（异常恢复）时也要有歌可播，别让播放停在结尾。
                    val song = current ?: list.firstOrNull()
                    if (song != null) playFrom(list, song)
                }
                else -> if (shouldAutoAdvance(mode, idx, list.size)) {
                    // LOOP_ALL / SHUFFLE：最后一首播完由 next() 取模回到第一首继续播。
                    // ORDER 在还没到末尾时同样往下走。
                    next()
                } else {
                    // 顺序播放到末尾：停在结尾，不自动回头。
                    // 用户手动点「下一曲」仍然会循环，见 next()。
                    progressJob?.cancel()
                    progress = 1f
                    positionMs = durationMs
                }
            }
            MusicExternal.notifyChanged(ctx)
        }
    }

    fun toggle() {
        // 状态是「在播」时，无论底层播放器是否还在，这一下都只做暂停。
        // 旧顺序先判 player 为空会走重建分支重新起一路，表现为「点暂停反而又播一遍」。
        if (isPlaying) {
            pause()
            return
        }
        val p = player
        if (p == null) {
            // 冷启动回来时 current 已经是上次那首歌，这里带上 positionMs 续播；
            // 否则会重新从 0:00 开始，正是用户反馈的“从头开始”。
            (current ?: queue().firstOrNull())?.let {
                playFrom(queue(), it, startPositionMs = positionMs)
            }
            return
        }
        // 还在 prepareAsync 阶段：先记下「准备完成后开播」，不要直接 start() 触发 IllegalStateException
        if (!prepared) {
            // 上次起播失败时播放器停在错误状态，再点播放不会自己恢复：
            // 直接重建当前这首歌，保证「暂停后再播放」永远有反应。
            if (lastError != null) {
                val song = current ?: queue().firstOrNull() ?: return
                playFrom(queue(), song, startPositionMs = positionMs)
                return
            }
            playWhenPrepared = true
            isPlaying = true
            // 准备阶段被暂停过的话，重新点播放时重新计时，避免播放器其实已经卡死
            armPrepareWatchdog(playGeneration)
            contextApp?.let { MusicExternal.notifyChanged(it) }
            return
        }
        // 用户手动点播放：系统音频焦点只用于「被打断时是否让路」，
        // 即使焦点被其他 App 占着也照常开播，避免按了播放却没有反应。
        resumeAfterFocusGain = false
        requestAudioFocus()
        runCatching { p.start() }
            .onSuccess {
                consecutiveErrors = 0
                isPlaying = true
                startProgressLoop()
                contextApp?.let { MusicExternal.notifyChanged(it) }
            }
            .onFailure {
                // MediaPlayer 已经进入错误状态，不能继续当作可播放；
                // 下次点播放会走上面的重建分支。
                lastError = "播放失败"
                isPlaying = false
                prepared = false
                playWhenPrepared = false
                contextApp?.let { MusicExternal.notifyChanged(it) }
            }
    }

    fun pause() {
        pauseInternal(abandonFocus = false)
        // 暂停就把听歌统计落一次盘，别等 30 秒攒批
        if (listenHasData) persistListenStats()
    }

    /** 音频焦点丢失：只暂停，不主动放弃焦点，等系统把焦点还回来 */
    private fun pauseFromFocusLoss() {
        pauseInternal(abandonFocus = false)
    }

    private fun pauseInternal(abandonFocus: Boolean) {
        if (!isPlaying && !abandonFocus) return
        // 准备阶段就按了暂停：prepared 回调里不要再自动 start
        playWhenPrepared = false
        if (isPlaying) {
            try { player?.pause() } catch (_: Exception) {}
            isPlaying = false
            // 还有定时/闹钟没到点时循环必须留着：睡眠定时与音乐闹钟都靠这个 tick 触发，
            // 以前无条件 cancel，导致「设了闹钟再暂停」之后闹钟永远不响。
            if (sleepEndMs <= 0L && alarmEndMs <= 0L) progressJob?.cancel()
            // 暂停瞬间把准确位置写下来，下次打开接着这一秒
            persistLastPlayed()
            contextApp?.let { MusicExternal.notifyChanged(it) }
        }
        if (abandonFocus) {
            resumeAfterFocusGain = false
            abandonAudioFocus()
        }
    }

    fun next() {
        // 「下一首播放」优先：不管什么播放模式，插队的歌一定先播。
        // consumeUpNext 会把插队歌落到队列里当前歌之后，播完自然回到原下一首。
        if (consumeUpNext()) return
        val list = queue()
        if (list.isEmpty()) return
        val idx = current?.let { c -> list.indexOfFirst { s -> s.id == c.id } } ?: -1
        // 手动点「下一曲」在任何模式下都循环：最后一首回到第一首。
        // 循环边界统一由 PlaylistOrder.nextTrackIndex 提供，别在这里另写一套取模。
        val nextIdx = when (mode) {
            PlayMode.SHUFFLE -> if (list.size > 1) {
                var n: Int
                do { n = (0 until list.size).random() } while (n == idx)
                n
            } else 0
            else -> nextTrackIndex(idx, list.size)
        }
        if (nextIdx in list.indices) playFrom(list, list[nextIdx])
    }

    fun prev() {
        val list = queue()
        if (list.isEmpty()) return
        val idx = current?.let { c -> list.indexOfFirst { s -> s.id == c.id } } ?: -1
        // 上一首就是当前列表里的上一首：不管播到第几秒都切歌，不回本曲开头。
        // 第一首循环到末尾（previousTrackIndex）；当前不在列表里时从第一首开始，
        // 保证按钮一定有反馈。所有模式一致，随机模式下也按顺序回退。
        val prevIdx = previousTrackIndex(idx, list.size)
        if (prevIdx in list.indices) playFrom(list, list[prevIdx])
    }

    /** 相对快进/快退（通知、耳机、小组件用） */
    fun seekBy(deltaMs: Long) {
        val dur = durationMs.takeIf { it > 0 } ?: return
        seekTo(((positionMs + deltaMs).toFloat() / dur).coerceIn(0f, 1f))
    }

    /** 拖动进度条到 0~1；成功返回 true，播放器没准备好 / 时长未知时返回 false */
    fun seekTo(fraction: Float): Boolean {
        val p = player ?: return false
        val dur = runCatching { p.duration }.getOrDefault(0)
        if (dur <= 0) return false
        val target = (fraction * dur).toInt()
        val ok = runCatching { p.seekTo(target); true }.getOrDefault(false)
        if (ok) {
            positionMs = target.toLong()
            progress = fraction
            persistLastPlayed()
            contextApp?.let { MusicExternal.notifyChanged(it) }
        }
        return ok
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
                .also { focusRequest = it }
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(focusListener)
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = tickerScope.launch {
            while (isActive) {
                val p = player ?: break
                // 暂停且没开睡眠定时/闹钟就退出循环；开着则继续留到点
                if (!isPlaying && sleepEndMs <= 0L && alarmEndMs <= 0L) break
                try {
                    val pos = p.currentPosition.toLong()
                    val dur = p.duration.takeIf { it > 0 } ?: 1
                    // durationMs 在一首歌里是常量，只在真正变化（切歌/首次拿到时长）时写，
                    // 否则每 500ms 重写同一个值会让所有读它的 composable 白白重组。
                    if (durationMs != dur.toLong()) durationMs = dur.toLong()
                    if (positionMs != pos) positionMs = pos
                    progress = pos.toFloat() / dur.toFloat()
                    // 音量 = min(结尾淡出, 闹钟淡入)：两个都不活跃就是满音量
                    val now = System.currentTimeMillis()
                    var vol = 1f
                    val remain = dur - pos
                    if (isPlaying && remain in 1..FADE_OUT_MS) {
                        vol = (remain.toFloat() / FADE_OUT_MS).coerceIn(0f, 1f)
                    }
                    if (alarmRampStartMs > 0) {
                        val ramp = ((now - alarmRampStartMs).toFloat() / ALARM_RAMP_MS).coerceIn(0f, 1f)
                        vol = minOf(vol, ramp)
                        if (ramp >= 1f) alarmRampStartMs = 0L
                    }
                    if (vol < 1f || fadeActive) {
                        fadeActive = vol < 1f
                        runCatching { p.setVolume(vol, vol) }
                    }
                    // 听歌年报：播放中每个 tick 记 500ms 收听时长，按天归档
                    if (isPlaying) {
                        addListenTime(500L)
                        if (statsDirtyAt > 0 && now - statsDirtyAt >= 30_000L) persistListenStats()
                    }
                    // 播放中每 4 秒记一次位置；最多丢几秒，不会每次 tick 都写盘
                    if (SystemClock.elapsedRealtime() - lastPersistAt >= 4_000L) {
                        persistLastPlayed()
                    }
                } catch (_: Exception) {}
                // 睡眠定时到点：柔和地暂停，不直接销毁播放器
                if (sleepEndMs > 0 && System.currentTimeMillis() >= sleepEndMs) {
                    sleepEndMs = 0L
                    pause()
                    break
                }
                // 音乐闹钟到点：清掉闹钟、淡入开播收藏（或上次队列）
                if (alarmEndMs > 0 && System.currentTimeMillis() >= alarmEndMs) {
                    alarmEndMs = 0L
                    persistAlarmEnd()
                    triggerAlarmStart()
                    break
                }
                // 播放中 500ms 保歌词同步精度；暂停只剩等定时时降到 1s，少一半唤醒
                delay(if (isPlaying) 500L else 1_000L)
            }
        }
    }

    // ---------- 新功能：播放模式 / 收藏 / 定时 / 均衡器 / 最近播放 ----------

    fun cycleMode() {
        mode = when (mode) {
            PlayMode.LOOP_ALL -> PlayMode.LOOP_ONE
            PlayMode.LOOP_ONE -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.ORDER
            PlayMode.ORDER -> PlayMode.LOOP_ALL
        }
        prefs().edit().putString(KEY_MODE, mode.name).apply()
        contextApp?.let { MusicExternal.notifyChanged(it) }
    }

    fun toggleFavorite(id: Long = current?.id ?: queue().firstOrNull()?.id ?: NO_SONG_ID): Boolean {
        // 网页缓存歌曲的 id 是负数（见 WebMusicCache），这里只排除“没有歌”的哨兵，
        // 不能再写成 id < 0：那会把所有网页歌曲的收藏都挡掉。
        if (id == NO_SONG_ID) return false
        val next = if (id in favorites) favorites - id else favorites + id
        favorites = next
        prefs().edit().putString(KEY_FAV, next.joinToString(",")).apply()
        contextApp?.let {
            MusicExternal.notifyChanged(it)
            MusicFavoritesSync.requestSync(it)
        }
        return id in next
    }

    fun isFavorite(id: Long = current?.id ?: queue().firstOrNull()?.id ?: NO_SONG_ID): Boolean =
        id != NO_SONG_ID && id in favorites

    /** 只加不删：截图批量导入时用，避免 toggle 在已收藏时把歌删掉 */
    fun markFavorite(id: Long) {
        if (id == NO_SONG_ID || id in favorites) return
        favorites = favorites + id
        prefs().edit().putString(KEY_FAV, favorites.joinToString(",")).apply()
        contextApp?.let {
            MusicExternal.notifyChanged(it)
            MusicFavoritesSync.requestSync(it)
        }
    }

    /**
     * 导出收藏的「歌名 + 歌手」，只给云同步用。
     *
     * 不返回 id、路径、封面或歌词；本地媒体库还没扫描时按 MediaStore 的 _ID 补查一次，
     * 保证旧收藏也能拿到可读的歌名歌手。
     */
    suspend fun favoriteRecords(context: Context): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            if (contextApp == null) init(context)
            val known = LinkedHashMap<Long, Pair<String, String>>()
            (songs + webSongs + pendingSongs).forEach { song ->
                val meta = applyMeta(song)
                known.putIfAbsent(meta.id, meta.title.trim() to cleanArtist(meta.artist))
            }
            val queried = queryMediaMeta(context, favorites.filter { it > 0 && it !in known })
            val out = ArrayList<Pair<String, String>>(favorites.size)
            favorites.forEach { id ->
                val pair = known[id] ?: queried[id] ?: return@forEach
                val title = pair.first.trim()
                if (title.isNotBlank()) out.add(title to pair.second)
            }
            out.distinctBy { MusicFavoritesSync.keyOf(it.first, it.second) }
        }

    private fun cleanArtist(value: String): String {
        val v = value.trim()
        return if (v in setOf("", "未知艺术家", "未知歌手", "<unknown>", "unknown")) "" else v
    }

    /**
     * 本地收藏里有多少首拿不到歌名/歌手（没权限、文件已删、媒体库还没扫描）。
     *
     * 这些收藏无法映射到云端稳定键，同步时必须保守：本次不生成任何删除清单，
     * 否则「查不到」会被误判成「用户删了」，把云端收藏整批清空。
     */
    suspend fun unresolvedFavoriteCount(context: Context): Int = withContext(Dispatchers.IO) {
        if (contextApp == null) init(context)
        val known = LinkedHashMap<Long, Pair<String, String>>()
        (songs + webSongs + pendingSongs).forEach { song ->
            val meta = applyMeta(song)
            known.putIfAbsent(meta.id, meta.title.trim() to cleanArtist(meta.artist))
        }
        val queried = queryMediaMeta(context, favorites.filter { it > 0 && it !in known })
        favorites.count { id ->
            val pair = known[id] ?: queried[id]
            pair == null || pair.first.isBlank()
        }
    }

    /** 按 MediaStore _ID 批量补查歌名/歌手；无权限或查询失败时返回空表，不影响收藏本身 */
    private fun queryMediaMeta(context: Context, ids: List<Long>): Map<Long, Pair<String, String>> {
        if (ids.isEmpty()) return emptyMap()
        return runCatching {
            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST
            )
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Audio.Media._ID} IN (${ids.joinToString(",")})",
                null,
                null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                buildMap {
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val rawTitle = c.getString(titleCol).orEmpty()
                        val rawArtist = c.getString(artistCol).orEmpty()
                        val parsed = SongNameParser.parse(rawTitle, rawArtist)
                        put(id, parsed.title.ifBlank { rawTitle } to cleanArtist(parsed.artist.orEmpty()))
                    }
                }
            } ?: emptyMap()
        }.getOrDefault(emptyMap())
    }

    /**
     * 云端收藏回灌：按歌名+歌手匹配本地已有歌曲。
     * 本机暂时没有的歌先建成「待获取」占位，点开播放时才去搜源；
     * 媒体库扫描到同名歌曲后会自动把占位替换成真实 id（见 reconcilePendingWithMedia）。
     */
    suspend fun mergeCloudFavorites(
        context: Context,
        records: List<Pair<String, String>>
    ): Int = withContext(Dispatchers.Main.immediate) {
        if (contextApp == null) init(context)
        val known = LinkedHashMap<String, LocalSong>()
        (songs + webSongs + pendingSongs).forEach { song ->
            val meta = applyMeta(song)
            val key = MusicFavoritesSync.keyOf(meta.title, meta.artist)
            known.putIfAbsent(key, meta)
        }

        val favs = favorites.toMutableSet()
        val pending = pendingSongs.toMutableList()
        var added = 0
        records.forEach { (rawTitle, rawArtist) ->
            val title = rawTitle.trim()
            if (title.isBlank()) return@forEach
            val artist = cleanArtist(rawArtist)
            val key = MusicFavoritesSync.keyOf(title, artist)
            val existing = known[key]
            val id = existing?.id ?: cloudPendingId(key)
            if (existing == null && pending.none { MusicFavoritesSync.keyOf(it.title, it.artist) == key }) {
                pending.add(
                    LocalSong(
                        id = id,
                        title = title,
                        artist = artist,
                        album = "云端收藏",
                        durationMs = 0L,
                        uri = Uri.EMPTY,
                        pendingSearch = true
                    )
                )
            }
            if (favs.add(id)) added++
        }
        if (added == 0) return@withContext 0

        favorites = favs
        pendingSongs = pending
        webSongs = pending + webSongs.filterNot { it.pendingSearch }
        prefs().edit().putString(KEY_FAV, favorites.joinToString(",")).apply()
        persistPending()
        rebuild()
        contextApp?.let { MusicExternal.notifyChanged(it) }
        added
    }

    /** 云端占位的稳定负数 id：同一首歌每次恢复都是同一个 id，不会反复堆重复项 */
    private fun cloudPendingId(key: String): Long {
        val n = runCatching { key.take(15).toLong(16) }.getOrDefault(0L)
        return -(n + 1L)
    }

    /** 换账号时清掉上一个账号的收藏/最近/统计，避免两个人的听歌数据混在一起 */
    suspend fun clearAccountMusicState(context: Context) = withContext(Dispatchers.Main.immediate) {
        if (contextApp == null) init(context)
        favorites = emptySet()
        pendingSongs = emptyList()
        recentIds = emptyList()
        playCounts = emptyMap()
        prefs().edit()
            .putString(KEY_FAV, "")
            .putString(KEY_RECENT, "")
            .putString(KEY_PLAY_COUNTS, "")
            .remove(KEY_PENDING)
            .remove(KEY_LISTEN_STATS)
            .apply()
        webSongs = webSongs.filterNot { it.pendingSearch }
        rebuild()
        contextApp?.let { MusicExternal.notifyChanged(it) }
    }

    /**
     * 长按「修改歌曲名」：写元数据覆盖，重启 / 重新扫描后依然生效。
     * 会同时刷新播放队列、当前播放项，歌词自动匹配也立刻用新歌名。
     */
    fun renameSong(context: Context, id: Long, newTitle: String): Boolean {
        val t = newTitle.trim()
        if (t.isBlank()) return false
        if (contextApp == null) init(context)
        val old = (songs + webSongs).firstOrNull { it.id == id } ?: return false
        return setSongMeta(context, id, t, old.artist)
    }

    /**
     * 长按「互换歌名和歌手」：修冷门歌手那种自动判不出方向的历史数据。
     * 交换结果写进 metaOverrides，之后重新扫描也不会被自愈逻辑再翻回去。
     */
    fun swapSongMeta(context: Context, id: Long): Boolean {
        if (contextApp == null) init(context)
        val old = (songs + webSongs).firstOrNull { it.id == id } ?: return false
        val t = old.artist.trim()
        if (t.isBlank()) return false
        return setSongMeta(context, id, t, old.title.trim())
    }

    /** 改名 / 互换共用的落库路径：覆盖元数据 + 回写列表 + 刷新通知与小组件 */
    private fun setSongMeta(context: Context, id: Long, title: String, artist: String): Boolean {
        if (contextApp == null) init(context)
        val t = title.trim()
        if (t.isBlank()) return false
        val a = artist.trim()
        metaOverrides = metaOverrides + (id to (t to a))
        persistMeta()
        // 导入歌曲的标题也回写一份，decodeCustom 与 applyMeta 双保险
        customSongs = customSongs.map { if (it.id == id) it.copy(title = t, artist = a) else it }
        persistCustom()
        webSongs = webSongs.map { if (it.id == id) it.copy(title = t, artist = a) else it }
        pendingSongs = pendingSongs.map { if (it.id == id) it.copy(title = t, artist = a) else it }
        if (pendingSongs.any { it.id == id }) persistPending()
        if (current?.id == id) current = current?.copy(title = t, artist = a)
        rebuild()
        contextApp?.let {
            MusicExternal.notifyChanged(it)
            // 收藏歌曲改名后歌名/歌手就是云端主键：旧键删除、新键新增
            if (id in favorites) MusicFavoritesSync.requestSync(it)
        }
        return true
    }

    /**
     * 长按「删除」：导入 / 网页缓存歌真删 App 私有文件；系统媒体库扫描到的歌
     * 只从 App 列表隐藏，绝不动用户手机里的原文件。顺带清收藏与最近播放。
     */
    fun deleteSong(context: Context, id: Long): Boolean {
        if (contextApp == null) init(context)
        val custom = customSongs.firstOrNull { it.id == id }
        val web = webSongs.firstOrNull { it.id == id }
        if (custom == null && web == null && songs.none { it.id == id }) return false
        val wasFavorite = id in favorites
        when {
            custom != null -> {
                runCatching { custom.uri.path?.let { File(it).delete() } }
                customSongs = customSongs.filterNot { it.id == id }
                persistCustom()
            }
            web != null -> {
                runCatching {
                    val f = File(web.path)
                    if (f.isFile) f.delete()
                    f.parentFile?.let { File(it, f.nameWithoutExtension + ".lrc").delete() }
                }
                webSongs = webSongs.filterNot { it.id == id }
                if (web.pendingSearch) {
                    pendingSongs = pendingSongs.filterNot { it.id == id }
                    persistPending()
                }
            }
            else -> {
                hiddenIds = hiddenIds + id
                persistHidden()
            }
        }
        favorites = favorites - id
        recentIds = recentIds.filterNot { it == id }
        metaOverrides = metaOverrides - id
        persistMeta()
        prefs().edit()
            .putString(KEY_FAV, favorites.joinToString(","))
            .putString(KEY_RECENT, recentIds.joinToString(","))
            .apply()
        rebuild()
        if (current?.id == id) {
            release()
            current = null
        }
        contextApp?.let {
            MusicExternal.notifyChanged(it)
            if (wasFavorite) MusicFavoritesSync.requestSync(it)
        }
        return true
    }

    /** @param minutes 0 = 取消定时 */
    fun setSleepTimer(minutes: Int) {
        sleepEndMs = if (minutes <= 0) 0L
        else System.currentTimeMillis() + minutes * 60_000L
        if (minutes <= 0) sleepAfterCurrent = false
        if (sleepEndMs > 0 && !isPlaying) startProgressLoop()
    }

    /** 「播完当前歌再停」：清掉分钟定时，改用歌曲完成事件触发 */
    fun setSleepAfterCurrent() {
        sleepEndMs = 0L
        sleepAfterCurrent = true
        if (!isPlaying) startProgressLoop()
    }

    fun sleepMinutesLeft(): Int =
        if (sleepEndMs <= 0L) 0
        else ((sleepEndMs - System.currentTimeMillis() + 59_999L) / 60_000L).toInt().coerceAtLeast(0)

    // ---------- 音乐闹钟 ----------

    /** 设置音乐闹钟：今天/明天最近的 HH:mm；App 进程存活时到点淡入播放 */
    fun setAlarmAt(hour: Int, minute: Int) {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(java.util.Calendar.MINUTE, minute.coerceIn(0, 59))
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        alarmEndMs = cal.timeInMillis
        persistAlarmEnd()
        // 睡着了播放器是暂停状态：循环要一直留到闹钟点
        if (!isPlaying) startProgressLoop()
    }

    fun cancelAlarm() {
        alarmEndMs = 0L
        persistAlarmEnd()
    }

    /** HH:mm 或空（未设置）；UI 显示用 */
    fun alarmText(): Pair<Int, Int>? =
        if (alarmEndMs <= 0L) null
        else {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = alarmEndMs }
            cal.get(java.util.Calendar.HOUR_OF_DAY) to cal.get(java.util.Calendar.MINUTE)
        }

    private fun persistAlarmEnd() {
        contextApp?.let {
            it.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_ALARM_END, alarmEndMs)
                .apply()
        }
    }

    /** 闹钟响铃：收藏优先，没收藏用上次队列，再退回整个曲库；10 秒音量淡入 */
    private fun triggerAlarmStart() {
        val favSongs = songsByIds(favorites.toList())
        val list = favSongs.ifEmpty { currentQueue.ifEmpty { songs + webSongs } }
        val first = list.firstOrNull() ?: return
        alarmRampStartMs = System.currentTimeMillis()
        sleepAfterCurrent = false
        runCatching { player?.setVolume(0f, 0f) }
        playFrom(list, first, if (favSongs.isNotEmpty()) QueueSource.FAVORITES else QueueSource.LIBRARY)
        contextApp?.let { MusicExternal.notifyChanged(it) }
    }

    // ---------- 听歌年报统计 ----------

    private fun addListenTime(deltaMs: Long) {
        listenTotalMs += deltaMs
        val key = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        listenByDayMs = listenByDayMs + (key to ((listenByDayMs[key] ?: 0L) + deltaMs))
        listenHasData = true
        if (statsDirtyAt <= 0L) statsDirtyAt = System.currentTimeMillis()
    }

    private fun persistListenStats() {
        statsDirtyAt = 0L
        val ctx = contextApp ?: return
        val o = JSONObject()
            .put("totalMs", listenTotalMs)
            .put("nightPlays", listenNightPlays)
            .put("totalPlays", listenTotalPlays)
            .put("hasData", listenHasData)
        o.put("byDay", JSONObject().also { arr ->
            listenByDayMs.forEach { (k, v) -> arr.put(k, v) }
        })
        o.put("byHour", JSONObject().also { arr ->
            listenByHour.forEach { (k, v) -> arr.put(k.toString(), v) }
        })
        o.put("hourSongs", JSONObject().also { arr ->
            listenHourSongs.forEach { (k, v) ->
                arr.put(k.toString(), JSONArray(v))
            }
        })
        o.put("daySongs", JSONObject().also { arr ->
            listenByDaySongs.forEach { (k, v) ->
                arr.put(k, JSONArray(v))
            }
        })
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LISTEN_STATS, o.toString())
            .apply()
    }

    private fun decodeListenStats(raw: String) {
        if (raw.isBlank()) return
        runCatching {
            val o = JSONObject(raw)
            listenTotalMs = o.optLong("totalMs", 0L)
            listenNightPlays = o.optInt("nightPlays", 0)
            listenTotalPlays = o.optInt("totalPlays", 0)
            listenHasData = o.optBoolean("hasData", false)
            val byDay = o.optJSONObject("byDay") ?: JSONObject()
            val map = LinkedHashMap<String, Long>()
            byDay.keys().forEach { key -> map[key] = byDay.optLong(key, 0L) }
            listenByDayMs = map
            val byHour = o.optJSONObject("byHour") ?: JSONObject()
            val hourMap = LinkedHashMap<Int, Int>()
            byHour.keys().forEach { key -> hourMap[key.toInt()] = byHour.optInt(key, 0) }
            listenByHour = hourMap
            val hourSongs = o.optJSONObject("hourSongs") ?: JSONObject()
            val hourSongMap = LinkedHashMap<Int, List<Long>>()
            hourSongs.keys().forEach { key ->
                val arr = hourSongs.optJSONArray(key) ?: JSONArray()
                val ids = (0 until arr.length()).mapNotNull { arr.optLong(it) }
                if (ids.isNotEmpty()) hourSongMap[key.toInt()] = ids
            }
            listenHourSongs = hourSongMap
            val daySongs = o.optJSONObject("daySongs") ?: JSONObject()
            val daySongMap = LinkedHashMap<String, List<Long>>()
            daySongs.keys().forEach { key ->
                val arr = daySongs.optJSONArray(key) ?: JSONArray()
                val ids = (0 until arr.length()).mapNotNull { arr.optLong(it) }
                if (ids.isNotEmpty()) daySongMap[key] = ids
            }
            listenByDaySongs = daySongMap
        }
    }

    /** 主动开播时记一次：总次数 +1；深夜（23–6 点）再记一笔；同时归档时段 / 当日歌曲 */
    private fun recordPlayEvent(songId: Long) {
        listenTotalPlays += 1
        val cal = java.util.Calendar.getInstance()
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        if (hour >= 23 || hour < 6) listenNightPlays += 1
        listenHasData = true
        listenByHour = listenByHour + (hour to ((listenByHour[hour] ?: 0) + 1))
        if (songId != NO_SONG_ID) {
            val hourList = (listOf(songId) + (listenHourSongs[hour] ?: emptyList()))
                .distinct().take(20)
            listenHourSongs = listenHourSongs + (hour to hourList)
            val dayKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date(cal.timeInMillis))
            val dayList = (listOf(songId) + (listenByDaySongs[dayKey] ?: emptyList()))
                .distinct().take(50)
            val nextDay = LinkedHashMap(listenByDaySongs)
            nextDay[dayKey] = dayList
            val keepKeys = nextDay.keys.sortedDescending().take(60)
            listenByDaySongs = nextDay.filterKeys { it in keepKeys }
        }
        if (statsDirtyAt <= 0L) statsDirtyAt = System.currentTimeMillis()
    }

    /** 听歌年报一键汇总：榜单、连听天数、峰值日都在这里算，UI 只管展示 */
    fun listenReport(): ListenStats {
        val artistCount = LinkedHashMap<String, Int>()
        playCounts.entries.sortedByDescending { it.value }.forEach { (id, count) ->
            val song = (songs + webSongs).firstOrNull { it.id == id } ?: return@forEach
            val name = song.artist.trim().ifBlank { tr("未知歌手", "Unknown") }
            artistCount[name] = (artistCount[name] ?: 0) + count
        }
        // 连听天数：从今天（或昨天）往回数，某天有收听就 +1
        var streak = 0
        val cal = java.util.Calendar.getInstance()
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        if ((listenByDayMs[fmt.format(cal.time)] ?: 0L) <= 0L) cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        while ((listenByDayMs[fmt.format(cal.time)] ?: 0L) > 0L) {
            streak++
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        val topDay = listenByDayMs.entries.maxByOrNull { it.value }
        return ListenStats(
            totalMs = listenTotalMs,
            totalPlays = listenTotalPlays,
            nightPlays = listenNightPlays,
            streakDays = streak,
            activeDays = listenByDayMs.count { it.value > 0L },
            topDayMs = topDay?.value ?: 0L,
            topDayLabel = topDay?.key.orEmpty(),
            topArtists = artistCount.entries.take(5).map { it.key to it.value }
        )
    }

    /** 清空听歌统计（年报页的「重置」入口；播放次数/最近播放不动） */
    fun resetListenStats() {
        listenTotalMs = 0L
        listenByDayMs = emptyMap()
        listenNightPlays = 0
        listenTotalPlays = 0
        listenHasData = false
        listenByHour = emptyMap()
        listenHourSongs = emptyMap()
        listenByDaySongs = emptyMap()
        statsDirtyAt = 0L
        persistListenStats()
    }

    /** 设置播放倍速并立即生效；部分 ROM 不支持变速时静默降级，不打断播放 */
    fun setSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(0.5f, 2f)
        player?.let { applySpeed(it) }
    }

    private fun applySpeed(mp: MediaPlayer) {
        if (!prepared) return
        runCatching {
            mp.playbackParams = mp.playbackParams.setSpeed(playbackSpeed).setPitch(1f)
        }
    }

    fun applyEqPreset(value: EqPreset) {
        eqPreset = value
        prefs().edit().putString(KEY_EQ, value.name).apply()
        // 走老的 6 档预设就等于离开音效库：两套滤镜不能叠着开，否则声音越调越怪
        soundFxOn = false
        prefs().edit().putBoolean(KEY_SOUND_FX_ON, false).apply()
        releaseEffects()
        applyEq()
    }

    private fun applyEq() {
        try {
            equalizer?.release()
            equalizer = null
            val session = player?.audioSessionId ?: return
            if (eqPreset == EqPreset.OFF) return
            val eq = Equalizer(0, session)
            val bands = eq.numberOfBands.toInt()
            val levels = eqPreset.levels
            val range = eq.bandLevelRange
            for (i in 0 until bands) {
                val want = if (i < levels.size) levels[i].toInt() else levels.last().toInt()
                eq.setBandLevel(i.toShort(), want.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
            }
            eq.enabled = true
            equalizer = eq
        } catch (_: Exception) {
            // 部分 ROM 不支持 Equalizer，静默降级，不影响播放
        }
    }

    // ───────────────────────── 音效库（真 DSP） ─────────────────────────

    /** 当前音效的实际参数：调音台那套从存盘里读回来 */
    fun currentSoundFx(): SoundFxSpec? {
        val id = soundFxId ?: return null
        if (id == SoundFxCatalog.CustomId) {
            return SoundFxSpec.parseCustom(prefs().getString(KEY_SOUND_FX_CUSTOM, null))
                ?: SoundFxCatalog.smart
        }
        return SoundFxCatalog.byId(id)
    }

    /** 选中一套音效并立刻生效（音效页「使用」按钮走这里） */
    fun applySoundFx(spec: SoundFxSpec) {
        soundFxId = spec.id
        soundFxOn = true
        eqPreset = EqPreset.OFF
        prefs().edit()
            .putString(KEY_SOUND_FX, spec.id)
            .putString(KEY_EQ, EqPreset.OFF.name)
            .putBoolean(KEY_SOUND_FX_ON, true)
            .apply()
        rebuildEffects(spec)
    }

    /** 调音台：把用户拖出来的六个参数当成一套自定义音效 */
    fun applyCustomSoundFx(
        bands: List<Float>,
        bass: Int,
        spatial: Int,
        reverb: Int,
        loudness: Int
    ) {
        val spec = SoundFxCatalog.customSpec(bands, bass, spatial, reverb, loudness)
        prefs().edit().putString(KEY_SOUND_FX_CUSTOM, spec.serialize()).apply()
        applySoundFx(spec)
    }

    /** 底部总开关：关掉就彻底回到原声 */
    fun setSoundFxEnabled(enable: Boolean) {
        soundFxOn = enable
        prefs().edit().putBoolean(KEY_SOUND_FX_ON, enable).apply()
        if (!enable) {
            releaseEffects()
            applyEq()
            return
        }
        val spec = currentSoundFx() ?: SoundFxCatalog.smart
        if (soundFxId == null) {
            soundFxId = spec.id
            prefs().edit().putString(KEY_SOUND_FX, spec.id).apply()
        }
        rebuildEffects(spec)
    }

    /** 换歌/重新 prepare 之后 audioSessionId 变了，效果器必须按新会话重建 */
    private fun restoreEffects() {
        if (soundFxOn) rebuildEffects(currentSoundFx() ?: SoundFxCatalog.smart) else applyEq()
    }

    private fun releaseEffects() {
        val effects = listOfNotNull(equalizer, bassBoost, spatializer, presetReverb, loudnessFx)
        effects.forEach { fx -> runCatching { fx.release() } }
        equalizer = null
        bassBoost = null
        spatializer = null
        presetReverb = null
        loudnessFx = null
    }

    /**
     * 按参数重建整条效果链。每一路都单独 runCatching：
     * 某些 ROM 会把 Virtualizer / Reverb 阉掉，不能让一处失败拖垮其他几路。
     */
    private fun rebuildEffects(spec: SoundFxSpec?) {
        releaseEffects()
        if (spec == null) return
        val session = runCatching { player?.audioSessionId ?: 0 }.getOrDefault(0)
        if (session == 0) return

        // 1) 五段 EQ
        runCatching {
            val eq = Equalizer(0, session)
            val bandCount = eq.numberOfBands.toInt().coerceAtLeast(1)
            val range = eq.bandLevelRange
            for (i in 0 until bandCount) {
                val centerHz = eq.getCenterFreq(i.toShort()) / 1000
                val wantDb = spec.bands[nearestAnchor(centerHz)]
                val level = (wantDb * 100).toInt()
                    .coerceIn(range[0].toInt(), range[1].toInt())
                eq.setBandLevel(i.toShort(), level.toShort())
            }
            eq.enabled = true
            equalizer = eq
        }

        // 2) 低频增强
        if (spec.bass > 0) runCatching {
            val bb = BassBoost(0, session)
            if (bb.strengthSupported) {
                bb.setStrength(spec.bass.coerceIn(0, 1000).toShort())
                bb.enabled = true
                bassBoost = bb
            } else {
                bb.release()
            }
        }

        // 3) 空间环绕
        if (spec.spatial > 0) runCatching {
            val v = Virtualizer(0, session)
            if (v.strengthSupported) {
                v.setStrength(spec.spatial.coerceIn(0, 1000).toShort())
                v.enabled = true
                spatializer = v
            } else {
                v.release()
            }
        }

        // 4) 混响（留声机/电台/影院这些全靠它）
        if (spec.reverb > 0) runCatching {
            val r = PresetReverb(0, session)
            r.preset = spec.reverb.toShort()
            r.enabled = true
            presetReverb = r
        }

        // 5) 响度提升（小音量下最明显）
        if (spec.loudness > 0) runCatching {
            val l = LoudnessEnhancer(session)
            l.setTargetGain(spec.loudness.coerceIn(0, 1000))
            l.enabled = true
            loudnessFx = l
        }
    }

    /** 设备频点数量和我们的 5 个锚点不一定对齐，取最近的那个 */
    private fun nearestAnchor(centerHz: Int): Int {
        val anchors = SoundFxCatalog.AnchorHz
        var best = 0
        var bestGap = Int.MAX_VALUE
        anchors.forEachIndexed { index, hz ->
            val gap = kotlin.math.abs(hz - centerHz)
            if (gap < bestGap) {
                bestGap = gap
                best = index
            }
        }
        return best
    }

    private fun pushRecent(id: Long) {
        recentIds = (listOf(id) + recentIds.filter { it != id }).take(30)
        playCounts = playCounts + (id to ((playCounts[id] ?: 0) + 1))
        // 听歌年报：每次主动开播记一笔（次数 + 深夜标记 + 时段/当日歌曲归档）
        recordPlayEvent(id)
        prefs().edit()
            .putString(KEY_RECENT, recentIds.joinToString(","))
            .putString(
                KEY_PLAY_COUNTS,
                playCounts.entries.joinToString(",") { "${it.key}:${it.value}" }
            )
            .apply()
    }

    /** 最近播放 / 收藏对应的歌曲列表（按记录顺序） */
    fun songsByIds(ids: List<Long>): List<LocalSong> =
        ids.mapNotNull { id -> (songs + webSongs).firstOrNull { it.id == id } }

    /**
     * 批量导入音频：把文件复制进私有目录并持久化，重启后依然在列表里。
     * 歌名识别走 [SongNameParser]，"1231红尘客栈.mp3" 会变成「红尘客栈」。
     * @return 成功导入的条数，UI 用它给明确反馈
     */
    fun addCustomSongs(context: Context, uris: List<Uri>): Int {
        if (uris.isEmpty()) return 0
        if (contextApp == null) init(context)
        val dir = File(context.filesDir, "music").apply { mkdirs() }
        val added = ArrayList<LocalSong>()
        uris.forEachIndexed { idx, uri ->
            runCatching {
                val display = displayName(context, uri) ?: "audio_${System.currentTimeMillis()}.mp3"
                val rawName = SongNameParser.parse(display).raw
                val safe = display.replace(Regex("""[\\/:*?"<>|]"""), "_")
                val dest = File(dir, "${System.currentTimeMillis()}_${idx}_$safe")
                val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                    dest.length()
                } ?: 0L
                if (copied < 1024L) {
                    dest.delete()
                    return@runCatching
                }

                var metaTitle: String? = null
                var metaArtist: String? = null
                var duration = 0L
                try {
                    val r = MediaMetadataRetriever()
                    r.setDataSource(dest.absolutePath)
                    metaTitle = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?.takeIf { it.isNotBlank() }
                    metaArtist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                    r.release()
                } catch (_: Exception) {
                    // 无 TAG 的文件很常见，直接用文件名识别
                }

                val fromTag = SongNameParser.parse(metaTitle ?: display, metaArtist)
                val fromName = SongNameParser.parse(display, metaArtist)
                added.add(
                    LocalSong(
                        id = System.currentTimeMillis() + idx,
                        title = fromTag.title.ifBlank { fromName.title }
                            .ifBlank { dest.nameWithoutExtension },
                        artist = fromTag.artist ?: fromName.artist ?: "未知艺术家",
                        album = "",
                        durationMs = duration,
                        uri = Uri.fromFile(dest),
                        path = dest.absolutePath,
                        rawTitle = rawName
                    )
                )
            }
        }
        if (added.isEmpty()) return 0
        customSongs = (customSongs + added).distinctBy { it.id }
        persistCustom()
        rebuild()
        return added.size
    }

    /** 单曲兼容入口（保留旧调用点） */
    fun addCustomSong(context: Context, uri: Uri) {
        addCustomSongs(context, listOf(uri))
    }

    /** 清空手动导入的歌曲，媒体库扫描结果不受影响 */
    fun clearCustomSongs(context: Context): Int {
        val n = customSongs.size
        customSongs.forEach { s -> runCatching { s.uri.path?.let { File(it).delete() } } }
        customSongs = emptyList()
        persistCustom()
        if (current?.uri?.scheme?.equals("file", ignoreCase = true) == true) release()
        rebuild()
        return n
    }

    fun customCount(): Int = customSongs.size

    /** SAF Uri 的显示名，导入时作为歌名识别的输入 */
    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0) c.getString(i) else null
        }
    }.getOrNull()

    private fun releaseInternal(abandonFocus: Boolean = true) {
        progressJob?.cancel()
        progressJob = null
        prepareWatchdog?.cancel()
        prepareWatchdog = null
        resumeAfterFocusGain = false
        if (abandonFocus) abandonAudioFocus()
        prepared = false
        playWhenPrepared = false
        releaseEffects()
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        isPlaying = false
    }

    fun release() {
        persistLastPlayed()
        persistListenStats()
        releaseInternal(abandonFocus = true)
        consecutiveErrors = 0
        contextApp?.let { MusicExternal.notifyStopped(it) }
    }

    // ---------- 摇一摇切歌 / 翻腕静音 ----------

    /** 摇一摇切歌开关：只在播放中生效，避免揣兜里误触把暂停变切歌 */
    fun setShakeNext(on: Boolean) {
        shakeNextOn = on
        prefs().edit().putBoolean(KEY_SHAKE_NEXT, on).apply()
        updateSensorListener()
    }

    /** 翻腕静音开关：屏幕朝下放平 1.5 秒自动暂停，正过来不会自动恢复（防误触发） */
    fun setFlipPause(on: Boolean) {
        flipPauseOn = on
        prefs().edit().putBoolean(KEY_FLIP_PAUSE, on).apply()
        updateSensorListener()
    }

    /** 两个传感器功能都关了就注销监听，不白白耗电 */
    private fun updateSensorListener() {
        val ctx = contextApp ?: return
        val sm = sensorManager
            ?: ctx.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        sensorManager = sm
        if (sm == null) return
        val want = shakeNextOn || flipPauseOn
        if (want && !sensorRegistered) {
            runCatching {
                sm.registerListener(
                    motionListener,
                    sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER),
                    android.hardware.SensorManager.SENSOR_DELAY_GAME
                )
            }
            sensorRegistered = true
        } else if (!want && sensorRegistered) {
            runCatching { sm.unregisterListener(motionListener) }
            sensorRegistered = false
        }
    }

    private val motionListener = object : android.hardware.SensorEventListener {
        private var lastShakeAt = 0L
        private var lastShakeFire = 0L
        private var faceDownSince = 0L

        override fun onSensorChanged(event: android.hardware.SensorEvent?) {
            if (event == null || event.values.size < 3) return
            val now = SystemClock.elapsedRealtime()
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            // 摇一摇：两次甩动的间隔节奏（80–600ms）+ 合加速度 2g 以上算一次有效甩动；
            // 1.2 秒内最多切一首，避免连续甩直接跳穿整个队列
            if (shakeNextOn && isPlaying) {
                val g = kotlin.math.sqrt(x * x + y * y + z * z)
                if (g >= 22f && now - lastShakeAt in 80..600 &&
                    now - lastShakeFire > 1_200L
                ) {
                    lastShakeFire = now
                    next()
                    contextApp?.let { MusicExternal.notifyChanged(it) }
                }
                if (now - lastShakeAt > 80) lastShakeAt = now
            }
            // 翻腕静音：x/y 接近水平、z 明显负值 = 屏幕朝下放平，持续 1.5 秒暂停一次
            val faceDown = z < -8.0f && kotlin.math.abs(x) < 4f && kotlin.math.abs(y) < 4f
            if (flipPauseOn && faceDown) {
                if (faceDownSince == 0L) {
                    faceDownSince = now
                } else if (isPlaying && now - faceDownSince >= 1_500L) {
                    faceDownSince = 0L
                    pause()
                }
            } else {
                faceDownSince = 0L
            }
        }

        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) = Unit
    }
}

/** 听歌年报汇总：数据层一次算好，年报页只管展示 */
data class ListenStats(
    /** 累计收听毫秒 */
    val totalMs: Long,
    /** 主动开播总次数 */
    val totalPlays: Int,
    /** 深夜（23–6 点）开播次数 */
    val nightPlays: Int,
    /** 连续每天至少听一会儿的天数 */
    val streakDays: Int,
    /** 有收听记录的天数 */
    val activeDays: Int,
    /** 听得最多的一天时长（毫秒） */
    val topDayMs: Long,
    /** 听得最多的一天日期 yyyy-MM-dd */
    val topDayLabel: String,
    /** 最常听歌手 Top5（歌手 to 播放次数） */
    val topArtists: List<Pair<String, Int>>
)
