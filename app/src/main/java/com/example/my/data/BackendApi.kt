package com.example.my.data

import android.content.Context
import android.net.Uri
import com.example.my.model.Comment
import com.example.my.model.CommentReply
import com.example.my.model.CostItem
import com.example.my.model.DayPlan
import com.example.my.model.Guide
import com.example.my.model.Route
import com.example.my.model.Spot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** 认证结果 */
sealed class AuthResult {
    data class Success(val user: RemoteUser) : AuthResult()
    /** needCaptcha：服务端要求先过图形验证码（登录连续失败或注册必填） */
    data class Failure(val message: String, val needCaptcha: Boolean = false) : AuthResult()
}

/** 图形验证码 */
data class Captcha(val id: String, val imageBase64: String)

/** 注册验证地址：地址里带一次性验证码，注册成功后服务端立即删除 */
data class VerifyLink(val url: String, val expiresIn: Int, val detail: String)

/** 注销申请状态 */
data class DeletionStatus(
    val pending: Boolean,
    val scheduledAt: String?,
    val daysLeft: Int,
    val graceDays: Int
)

/** 远端用户资料（对应后端 /users/me） */
data class RemoteUser(
    val id: Int,
    val nickname: String,
    val signature: String,
    val avatar: String?,
    val citiesCount: Int,
    val footprintCount: Int,
    val daysCount: Int,
    val followers: Int,
    val following: Int,
    val likesReceived: Int,
    val defaultPublic: Boolean,
    val notifEnabled: Boolean,
    val shareEnabled: Boolean,
    /** 登录用户名（后端唯一，大小写不敏感） */
    val username: String = "",
    /** 注册时间（yyyy-MM-dd），用于"我的"页展示 */
    val createdAt: String? = null
)

/** 私信对方资料（后端 /api/messages 的 peer 字段） */
data class DmPeer(
    val id: Int,
    val username: String,
    val nickname: String,
    val avatar: String?,
    val signature: String
)

/**
 * 私信会话。
 * [relationshipKnown] 为 false 表示当前后端还没返回互关字段，
 * 此时 App 不擅自限制发送，等后端补齐后再按 [mutual]/[canSend] 执行。
 */
data class DmConversation(
    val id: Int,
    val peer: DmPeer,
    val unread: Int,
    val lastPreview: String,
    val lastMessageAt: String,
    val iFollowPeer: Boolean,
    val peerFollowsMe: Boolean,
    val mutual: Boolean,
    val canSend: Boolean,
    val remainingMessages: Int,
    val relationshipKnown: Boolean,
    val lastMessageId: Int = 0
)

data class DmMessage(
    val id: Int,
    val seq: Int,
    val body: String,
    val mine: Boolean,
    val recalled: Boolean,
    val createdAt: String,
    val replyToId: Int? = null,
    val replyToBody: String? = null,
    val replyToMine: Boolean = false,
    /** text / image / video */
    val type: String = "text",
    val mediaUrl: String? = null,
    val thumbUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Long? = null,
    val mediaBytes: Long? = null,
    val mediaMime: String? = null,
    /** 本地乐观态：尚未收到服务端回执的待发送消息，仅本机 UI 使用，后端不返回 */
    val pending: Boolean = false,
    /** 超级加密通信：服务端 extra.e2ee 里的不透明信封（正文在客户端解密） */
    val e2eeEnvelope: String? = null,
    /** 已解密的展示正文；null 表示普通明文消息，直接用 body */
    val displayBody: String? = null,
    /** 已解密的引用正文 */
    val displayReplyToBody: String? = null,
    /** 纯握手消息：只用于交换公钥，不进入聊天气泡 */
    val e2eeHandshake: Boolean = false
)

data class DmThread(
    val conversation: DmConversation?,
    val messages: List<DmMessage>
)

/** SSE `event: message` 的一行数据；后端同时带 conversation_id 和消息体 */
data class DmStreamEvent(
    val conversationId: Int,
    val message: DmMessage
)

data class DmSendResult(
    val ok: Boolean,
    val message: DmMessage? = null,
    val conversation: DmConversation? = null,
    val error: String? = null
)

data class UserLookup(
    val user: DmPeer,
    val following: Boolean,
    val followsMe: Boolean,
    val mutual: Boolean
)

data class FollowState(
    val following: Boolean,
    val followsMe: Boolean,
    val mutual: Boolean
)

data class FollowEvent(
    val id: Int,
    val actor: DmPeer,
    val createdAt: String,
    val read: Boolean,
    val iFollowActor: Boolean
)

/** 粉丝 / 关注列表中的一个用户（带与当前用户的关系，用于按钮态与互关标记） */
data class FollowUser(
    val peer: DmPeer,
    val following: Boolean,
    val followsMe: Boolean,
    val mutual: Boolean
)

/** 他人主页：资料 + 与我的关注关系 + 攻略数 */
data class UserProfileInfo(
    val user: RemoteUser,
    val following: Boolean,
    val followsMe: Boolean,
    val mutual: Boolean,
    val guideCount: Int
)

/**
 * MY足迹 后端 API 客户端（地址见 [CloudConfig.SERVER_URL]）。
 * 风格与 WeatherApi 一致：HttpURLConnection + org.json，无第三方依赖。
 * 所有函数失败时返回 null/false，由调用方决定降级（AppState 静默回落本地数据）。
 * 安全约定：地址必须是 HTTPS，且证书能被系统根证书校验；禁止退回明文 HTTP。
 * 开关关闭（[CloudConfig.ENABLED] = false）时所有请求在这里短路，不会产生任何网络流量。
 */
object BackendApi {

    /** 服务端根地址：跟随云端开关，开关关闭时不会用到 */
    val BASE_URL: String get() = CloudConfig.SERVER_URL

    /**
     * 只探测服务器是否活着，不受云端开关影响、不带 token。
     * 离线模式要靠它判断“是网络不可达，还是账号失效”，不能把网络失败误判成 401。
     */
    suspend fun pingServer(timeoutMs: Int = 2500): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(BASE_URL + "/healthz").openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
            }
            try {
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }

    var token: String? = null
        private set
    var remoteUserId: Int? = null
        private set(value) {
            field = value
            UserScope.setUid(value)
        }

    /** 401 全局回调（token 失效时由 AppState 强制登出） */
    var onUnauthorized: (() -> Unit)? = null

    val loggedIn: Boolean get() = token != null

    /** 使用本地缓存的 token 直接登录态恢复；返回 token 是否仍有效 */
    suspend fun restoreToken(saved: String): Boolean = withContext(Dispatchers.IO) {
        token = saved
        val me = fetchMe() ?: return@withContext false
        // 关键：冷启动也必须恢复「我是谁」，否则全量恢复、换账号隔离都拿不到 uid
        remoteUserId = me.id
        true
    }

    fun clearToken() {
        token = null
        remoteUserId = null
    }

    /**
     * 把接口返回的相对路径补成完整 URL。
     * 本地模式下头像等资源是 files/ 里的绝对路径（file:// 或 /data/...），原样返回；
     * 只有真正需要拼服务端域名的相对路径才会用到 [BASE_URL]。
     */
    fun fullUrl(path: String?): String? {
        val p = path?.takeIf { it.isNotBlank() } ?: return null
        if (p.startsWith("file://") || p.startsWith("content://")) return p
        // 媒体文件统一归一到当前服务端：旧版本可能把局域网地址（192.168.x.x）
        // 或 127.0.0.1 写进云端，换到公网后原样打开必失败。
        if (p.startsWith("http")) {
            val uploadsAt = p.indexOf("/uploads/")
            return if (uploadsAt >= 0) BASE_URL + p.substring(uploadsAt) else p
        }
        // 后端上传返回的媒体地址是 /uploads/...，是相对服务端的路径，必须补域名；
        // 不能把所有以 / 开头的值都当成设备本地绝对路径，否则图片永远加载不出来。
        if (p.startsWith("/uploads/") || p.startsWith("/api/") || p.startsWith("/admin/")) {
            return BASE_URL + p
        }
        if (p.startsWith("/")) return p
        return if (CloudConfig.ENABLED) BASE_URL + p else null
    }

    // ---------- 密码安全 ----------

    /**
     * 固定盐预哈希：SHA-256(盐 : 密码)，不含账号标识。
     * 注册、登录、注销都用它；明文密码不出设备。
     */
    fun hashPassword(password: String): String {
        val raw = "myzuji_salt_v2:$password"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** 从 request() 抛出的 "HTTP <code>: <body>" 消息里取状态码；取不到返回 -1 */
    fun httpCodeOf(message: String?): Int {
        val m = message ?: return -1
        return Regex("HTTP (\\d+)").find(m)?.groupValues?.get(1)?.toIntOrNull() ?: -1
    }

    /** 429 限流响应里的 Retry-After 秒数；缺省返回 0 */
    fun retryAfterOf(message: String?): Long {
        val m = message ?: return 0L
        return Regex("Retry-After[=: ]+(\\d+)", RegexOption.IGNORE_CASE).find(m)
            ?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }

    // ---------- 认证 ----------

    /**
     * 用户名登录。密码为固定盐预哈希，服务端直接 bcrypt 校验。
     */
    suspend fun login(
        username: String,
        passwordHash: String,
        captchaId: String? = null,
        captchaText: String? = null
    ): AuthResult = authRequest(
        "/auth/login",
        JSONObject().apply {
            put("username", username.trim())
            put("password_hash", passwordHash)
            captchaId?.let { put("captcha_id", it) }
            captchaText?.let { put("captcha_text", it) }
        }
    )

    /** 注册：用户名 + 图形验证码 + 密码（密码为固定盐预哈希）。 */
    suspend fun register(
        username: String,
        passwordHashV2: String,
        nickname: String,
        captchaId: String,
        captchaText: String
    ): AuthResult = authRequest(
        "/auth/register",
        JSONObject().apply {
            put("username", username.trim())
            put("password_hash", passwordHashV2)
            if (nickname.isNotBlank()) put("nickname", nickname.trim())
            put("captcha_id", captchaId)
            put("captcha_text", captchaText)
        }
    )

    /** 申请注册验证地址：地址里带一次性验证码，打开后回 App 注册。 */
    suspend fun requestVerifyLink(
        username: String,
        captchaId: String,
        captchaText: String
    ): Pair<VerifyLink?, String?> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("username", username.trim())
                put("captcha_id", captchaId)
                put("captcha_text", captchaText)
            }
            val o = JSONObject(request("POST", "/auth/register_link", body.toString(), auth = false))
            VerifyLink(
                url = o.getString("verify_url"),
                expiresIn = o.optInt("expires_in", 900),
                detail = o.optString("detail")
            ) to null
        } catch (e: Exception) {
            val msg = e.message ?: ""
            null to when {
                msg.contains("HTTP 409") -> parseDetail(msg) ?: "该用户名已被注册"
                msg.contains("HTTP 429") -> parseDetail(msg) ?: "生成太频繁，请稍后再试"
                msg.contains("HTTP 400") -> parseDetail(msg) ?: "图形验证码错误或已过期"
                msg.contains("HTTP") -> parseDetail(msg) ?: "生成失败，请稍后再试"
                else -> "网络异常，请检查网络连接"
            }
        }
    }

    /** 拉取图形验证码（base64 PNG），失败返回 null */
    suspend fun fetchCaptcha(): Captcha? = withContext(Dispatchers.IO) {
        runCatching {
            val o = JSONObject(request("GET", "/auth/captcha", auth = false))
            Captcha(o.getString("captcha_id"), o.getString("image"))
        }.getOrNull()
    }

    /** 刷新令牌：用当前 token 换新 access_token；失败返回 false（调用方按 401 登出处理） */
    suspend fun refreshToken(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(request("POST", "/auth/refresh", "{}"))
            root.optString("access_token").takeIf { it.isNotBlank() }?.let { token = it }
            true
        }.getOrDefault(false)
    }

    /** 登出：通知服务端作废当前 token；无论服务端成败都清本地登录态 */
    suspend fun logoutRemote() {
        withContext(Dispatchers.IO) {
            runCatching { request("POST", "/auth/logout", "{}") }
        }
        clearToken()
    }

    // ---------- 注销账号 ----------

    /** 查询注销申请：返回 (pending, scheduledAt, daysLeft, 错误信息) */
    suspend fun fetchDeletionStatus(): Pair<DeletionStatus?, String?> = withContext(Dispatchers.IO) {
        try {
            val o = JSONObject(request("GET", "/api/account/deletion"))
            DeletionStatus(
                pending = o.optBoolean("pending"),
                scheduledAt = o.optString("scheduled_at").ifBlank { null },
                daysLeft = o.optInt("days_left"),
                graceDays = o.optInt("grace_days", 7)
            ) to null
        } catch (e: Exception) {
            null to (e.message ?: "查询失败")
        }
    }

    /** 提交注销申请：7 天冷静期内可撤销；到期删除账号与云端数据。 */
    suspend fun requestAccountDeletion(
        passwordHash: String
    ): Pair<DeletionStatus?, String?> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("password_hash", passwordHash)
            }
            val o = JSONObject(request("POST", "/api/account/deletion", body.toString()))
            DeletionStatus(
                pending = o.optBoolean("pending", true),
                scheduledAt = o.optString("scheduled_at").ifBlank { null },
                daysLeft = o.optInt("days_left"),
                graceDays = o.optInt("grace_days", 7)
            ) to null
        } catch (e: Exception) {
            val msg = e.message ?: ""
            null to when {
                msg.contains("HTTP 401") -> "密码不正确"
                msg.contains("HTTP 429") -> parseDetail(msg) ?: "操作太频繁，请稍后再试"
                msg.contains("HTTP") -> parseDetail(msg) ?: "申请失败，请稍后再试"
                else -> "网络异常，请检查网络连接"
            }
        }
    }

    /** 撤销注销申请。 */
    suspend fun cancelAccountDeletion(): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            request("DELETE", "/api/account/deletion")
            true to null
        } catch (e: Exception) {
            false to (parseDetail(e.message ?: "") ?: "撤销失败，请稍后再试")
        }
    }

    private suspend fun authRequest(path: String, body: JSONObject): AuthResult = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(request("POST", path, body.toString(), auth = false))
            token = root.getString("access_token")
            val u = root.getJSONObject("user")
            remoteUserId = u.getInt("id")
            AuthResult.Success(parseRemoteUser(u))
        } catch (e: Exception) {
            token = null
            val msg = e.message ?: ""
            val need = parseNeedCaptcha(msg)
            AuthResult.Failure(
                message = when {
                    msg.contains("HTTP 401") -> parseDetail(msg) ?: "用户名或密码错误"
                    msg.contains("HTTP 409") -> parseDetail(msg) ?: "该用户名已被注册"
                    msg.contains("HTTP 403") -> parseDetail(msg) ?: "当前网络注册账号已达上限"
                    msg.contains("HTTP 400") -> parseDetail(msg) ?: "请先打开注册验证地址"
                    msg.contains("HTTP 429") -> parseDetail(msg) ?: "请求过于频繁，请稍后再试"
                    msg.contains("HTTP") -> parseDetail(msg) ?: "服务器错误，请稍后再试"
                    else -> "网络异常，请检查网络连接"
                },
                needCaptcha = need
            )
        }
    }

    private fun parseDetail(msg: String): String? = runCatching {
        JSONObject(msg.substringAfter(": ")).optString("detail").ifBlank { null }
    }.getOrNull()

    /** 服务端要求图形验证码的信号：{"detail": "...", "need_captcha": true} */
    private fun parseNeedCaptcha(msg: String): Boolean = runCatching {
        JSONObject(msg.substringAfter(": ")).optBoolean("need_captcha")
    }.getOrDefault(false)

    // ---------- 用户 ----------

    suspend fun fetchMe(): RemoteUser? = withContext(Dispatchers.IO) {
        runCatching {
            parseRemoteUser(JSONObject(request("GET", "/users/me")))
        }.getOrNull()
    }

    /** 查看他人主页资料（含与我的关注关系）；后端未开放 / 不存在返回 null */
    suspend fun fetchUserProfile(userId: Int): UserProfileInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(request("GET", "/users/$userId"))
            val data = root.optJSONObject("data") ?: root
            UserProfileInfo(
                user = parseRemoteUser(data),
                following = data.optBoolean("following", false),
                followsMe = data.optBoolean("follows_me", false),
                mutual = data.optBoolean("mutual", false),
                guideCount = data.optInt("guides", 0)
            )
        }.getOrNull()
    }

    suspend fun updateMe(
        nickname: String? = null,
        signature: String? = null,
        defaultPublic: Boolean? = null,
        notifEnabled: Boolean? = null,
        shareEnabled: Boolean? = null
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                nickname?.let { put("nickname", it) }
                signature?.let { put("signature", it) }
                defaultPublic?.let { put("default_public", it) }
                notifEnabled?.let { put("notif_enabled", it) }
                shareEnabled?.let { put("share_enabled", it) }
            }
            request("PUT", "/users/me", body.toString())
            true
        }.getOrDefault(false)
    }

    /** 上传头像，返回服务端相对路径（/uploads/avatars/xxx.jpg） */
    suspend fun uploadAvatar(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            val root = JSONObject(
                multipartUpload("/users/me/avatar", "file", "avatar.jpg", bytes)
            )
            root.getString("url")
        }.getOrNull()
    }

    // ---------- 私信 / 关注 ----------

    /** 会话列表；后端不可达/未登录时返回 null，由界面显示离线提示 */
    suspend fun fetchConversations(): List<DmConversation>? = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(request("GET", "/api/messages/conversations"))
            val data = root.optJSONObject("data")
            val arr = data?.optJSONArray("items") ?: root.optJSONArray("items") ?: JSONArray()
            (0 until arr.length()).map { parseDmConversation(arr.getJSONObject(it)) }
        }.getOrNull()
    }

    /** 打开（或创建）与某人的会话 */
    suspend fun openConversation(peerId: Int): DmConversation? = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("peer_id", peerId)
            val root = JSONObject(request("POST", "/api/messages/conversations", body.toString()))
            parseDmConversation(root.optJSONObject("data") ?: root)
        }.getOrNull()
    }

    /** 拉取某个会话的最近消息 + 会话状态 */
    suspend fun fetchThread(conversationId: Int, limit: Int = 80): DmThread? =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = JSONObject(
                    request("GET", "/api/messages/conversations/$conversationId/messages?limit=$limit")
                )
                val data = root.optJSONObject("data") ?: root
                val arr = data.optJSONArray("items") ?: JSONArray()
                DmThread(
                    conversation = data.optJSONObject("conversation")?.let { parseDmConversation(it) },
                    messages = (0 until arr.length()).map { parseDmMessage(arr.getJSONObject(it)) }
                )
            }.getOrNull()
        }

    /**
     * 发一条私信。type=text 用 text 内容；type=image/video 时 [media] 携带
     * url/thumb/width/height/duration/bytes/mime 等媒体元信息。
     * 失败时把后端 detail 原样带回给界面；replyToId 为被回复消息（引用回复）。
     */
    suspend fun sendDmMessage(
        conversationId: Int,
        text: String,
        replyToId: Int? = null,
        type: String = "text",
        media: JSONObject? = null,
        /** 超级加密通信的不透明信封；服务端只原样存进 extra.e2ee */
        e2eeEnvelope: String? = null
    ): DmSendResult =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("type", type)
                    put("body", text.trim())
                    put("client_msg_id", UUID.randomUUID().toString())
                    replyToId?.let { put("reply_to_id", it) }
                    val extra = JSONObject()
                    if (media != null) {
                        val keys = media.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            extra.put(k, media.opt(k))
                        }
                    }
                    if (!e2eeEnvelope.isNullOrBlank()) {
                        extra.put("e2ee", e2eeEnvelope)
                    }
                    if (extra.length() > 0) put("extra", extra)
                }
                val root = JSONObject(
                    request("POST", "/api/messages/conversations/$conversationId/messages", body.toString())
                )
                val data = root.optJSONObject("data") ?: root
                DmSendResult(
                    ok = true,
                    message = data.optJSONObject("message")?.let { parseDmMessage(it) },
                    conversation = data.optJSONObject("conversation")?.let { parseDmConversation(it) }
                )
            } catch (e: Throwable) {
                DmSendResult(ok = false, error = humanError(e))
            }
        }

    /** 上报已读水位，失败不影响界面 */
    suspend fun markConversationRead(conversationId: Int, seq: Int) {
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().put("seq", seq)
                request("POST", "/api/messages/conversations/$conversationId/read", body.toString())
            }
        }
    }

    /** 撤回一条自己发出的消息；后端未开放或失败返回 false，界面按本地兜底标记 */
    suspend fun recallMessage(messageId: Int): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                request("POST", "/api/messages/recall/$messageId", "{}")
                true
            }.getOrDefault(false)
        }

    /** 删除一条消息的服务端记录；失败返回 false，界面仍可本地移除记录 */
    suspend fun deleteMessage(conversationId: Int, messageId: Int): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                request("DELETE", "/api/messages/conversations/$conversationId/messages/$messageId")
                true
            }.getOrDefault(false)
        }

    /** 总未读数；后台保活服务启动时用来校准红点 */
    suspend fun fetchUnreadTotal(): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(request("GET", "/api/messages/unread"))
            val data = root.optJSONObject("data") ?: root
            data.optInt("total", 0)
        }.getOrNull()
    }

    /**
     * SSE 流地址。token 不放在 query，避免被网关日志记录；
     * 由调用方在请求头里带 Authorization。
     */
    fun messageStreamUrl(sinceId: Long): String =
        "$BASE_URL/api/messages/stream?since_id=$sinceId"

    /** 把 SSE 的 data 行解析成事件；格式不对返回 null，调用方直接跳过 */
    fun parseDmStreamEvent(raw: String): DmStreamEvent? = runCatching {
        val o = JSONObject(raw)
        DmStreamEvent(
            conversationId = o.optInt("conversation_id"),
            message = parseDmMessage(o)
        )
    }.getOrNull()

    /**
     * 按账号（用户名）精确查找用户。
     */
    suspend fun lookupUserByAccount(account: String): UserLookup? = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode(account.trim(), "UTF-8")
            val root = JSONObject(request("GET", "/users/search?username=$q"))
            val data = root.optJSONObject("data") ?: root
            val userObj = data.optJSONObject("user") ?: data.optJSONObject("peer") ?: data
            val mutual = data.optBoolean("mutual", false)
            UserLookup(
                user = parseDmPeer(userObj),
                following = data.optBoolean("following", false),
                followsMe = data.optBoolean("follows_me", mutual),
                mutual = mutual
            )
        }.getOrNull()
    }

    /** 关注/取消关注，复用后端已有的 POST /users/{id}/follow */
    suspend fun toggleFollow(userId: Int): FollowState? = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(request("POST", "/users/$userId/follow"))
            val data = root.optJSONObject("data") ?: root
            FollowState(
                following = data.optBoolean("following", false),
                followsMe = data.optBoolean("follows_me", false),
                mutual = data.optBoolean("mutual", false)
            )
        }.getOrNull()
    }

    /**
     * 关注通知列表。
     * 依赖后端新增的 GET /users/notifications；未开放时返回 null。
     */
    suspend fun fetchFollowEvents(): List<FollowEvent>? = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(request("GET", "/users/notifications"))
            val data = root.optJSONObject("data") ?: root
            val arr = data.optJSONArray("items") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val actor = o.optJSONObject("actor") ?: o.optJSONObject("user") ?: return@mapNotNull null
                FollowEvent(
                    id = o.optInt("id", i),
                    actor = parseDmPeer(actor),
                    createdAt = o.optString("created_at"),
                    read = o.optBoolean("read", false),
                    iFollowActor = o.optBoolean("i_follow_actor", o.optBoolean("following", false))
                )
            }
        }.getOrNull()
    }

    suspend fun markFollowEventsRead() {
        withContext(Dispatchers.IO) {
            runCatching { request("POST", "/users/notifications/read", "{}") }
        }
    }

    /** 粉丝列表；后端未开放 /users/followers 时返回 null，界面按空态提示，不伪造数据 */
    suspend fun fetchFollowers(): List<FollowUser>? = fetchUserList("/users/followers")

    /** 我关注的列表；后端未开放 /users/following 时返回 null */
    suspend fun fetchFollowing(): List<FollowUser>? = fetchUserList("/users/following")

    private suspend fun fetchUserList(path: String): List<FollowUser>? = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(request("GET", path))
            val data = root.optJSONObject("data") ?: root
            val arr = data.optJSONArray("items")
                ?: data.optJSONArray("users")
                ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val peerObj = o.optJSONObject("user") ?: o.optJSONObject("actor") ?: o
                val mutual = o.optBoolean("mutual", false)
                FollowUser(
                    peer = parseDmPeer(peerObj),
                    following = o.optBoolean("following", o.optBoolean("i_follow_peer", false)),
                    followsMe = o.optBoolean("follows_me", mutual),
                    mutual = mutual
                )
            }
        }.getOrNull()
    }

    private fun parseDmPeer(o: JSONObject): DmPeer = DmPeer(
        id = o.optInt("id"),
        username = o.optString("username", ""),
        nickname = o.optString("nickname", "旅行者"),
        avatar = o.optString("avatar").ifBlank { null },
        signature = o.optString("signature", "")
    )

    private fun parseDmConversation(o: JSONObject): DmConversation {
        val peer = o.optJSONObject("peer")?.let { parseDmPeer(it) }
            ?: DmPeer(o.optInt("peer_id"), "", "旅行者", null, "")
        val known = o.has("mutual") || o.has("i_follow_peer") || o.has("peer_follows_me")
        val mutual = o.optBoolean("mutual", false)
        return DmConversation(
            id = o.optInt("id"),
            peer = peer,
            unread = o.optInt("unread"),
            lastPreview = o.optString("last_preview"),
            lastMessageAt = o.optString("last_message_at"),
            iFollowPeer = o.optBoolean("i_follow_peer", mutual),
            peerFollowsMe = o.optBoolean("peer_follows_me", mutual),
            mutual = mutual,
            // 字段缺失时先放行，避免旧后端把正常会话锁死；字段到位后严格按后端 can_send 执行
            canSend = o.optBoolean("can_send", if (known) mutual else true),
            remainingMessages = o.optInt(
                "remaining_messages",
                if (!known) Int.MAX_VALUE else if (mutual) 999 else 1
            ),
            relationshipKnown = known,
            lastMessageId = o.optInt("last_message_id")
        )
    }

    private fun parseDmMessage(o: JSONObject): DmMessage {
        val reply = o.optJSONObject("reply_to") ?: o.optJSONObject("quote")
        val replyId = reply?.optInt("id")?.takeIf { it != 0 }
            ?: o.optInt("reply_to_id").takeIf { it != 0 }
        val replySenderId = reply?.optInt("sender_id") ?: reply?.optInt("senderId") ?: 0
        val extra = o.optJSONObject("extra") ?: JSONObject()
        val type = o.optString("type", "text").ifBlank { "text" }
        val mediaUrl = extra.optString("url").ifBlank { null }
        val thumbUrl = extra.optString("thumb").ifBlank { null }
        return DmMessage(
            id = o.optInt("id"),
            seq = o.optInt("seq"),
            body = o.optString("body"),
            mine = o.optBoolean("mine"),
            recalled = o.optBoolean("recalled") || o.optInt("status", 1) == 0,
            createdAt = o.optString("created_at"),
            replyToId = replyId,
            replyToBody = reply?.optString("body") ?: o.optString("reply_to_body").takeIf { it.isNotBlank() },
            replyToMine = replySenderId != 0 && replySenderId == remoteUserId,
            type = type,
            mediaUrl = mediaUrl,
            thumbUrl = thumbUrl,
            width = extra.optInt("width").takeIf { it != 0 },
            height = extra.optInt("height").takeIf { it != 0 },
            duration = extra.optLong("duration").takeIf { it != 0L },
            mediaBytes = extra.optLong("bytes").takeIf { it != 0L },
            mediaMime = extra.optString("mime").ifBlank { null },
            e2eeEnvelope = extra.optString("e2ee").takeIf { it.isNotBlank() }
        )
    }

    private fun humanError(e: Throwable): String {
        val raw = e.message ?: return "网络异常，请稍后重试"
        val code = httpCodeOf(raw)
        val payload = raw.substringAfter(": ", raw)
        val detail = runCatching { JSONObject(payload).optString("detail") }.getOrNull()
            ?.takeIf { it.isNotBlank() }
        return when (code) {
            400 -> detail ?: "提交信息有误，请检查后重试"
            401 -> detail ?: "登录已失效，请重新登录"
            403 -> "账号已被限制操作，请联系客服"
            404 -> detail ?: "内容不存在或已被删除"
            413 -> "内容过大，已超出上传上限"
            429 -> {
                val wait = retryAfterOf(raw)
                if (wait > 0) "操作过于频繁，请 ${wait} 秒后再试" else "操作过于频繁，请稍后再试"
            }
            500 -> "服务器开小差了，请稍后重试"
            else -> detail ?: payload.ifBlank { "网络异常，请稍后重试" }
        }
    }

    // ---------- 攻略 ----------

    suspend fun fetchGuides(skip: Int = 0, limit: Int = 50): List<Guide>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val arr = JSONArray(request("GET", "/guides?skip=$skip&limit=$limit", auth = false))
                (0 until arr.length()).map { parseGuide(arr.getJSONObject(it)) }
            }.getOrNull()
        }

    suspend fun createGuide(g: Guide): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("title", g.title)
                put("intro", g.intro)
                put("city", g.city)
                put("days", g.days)
                put("cost", g.cost)
                put("theme", g.theme)
                put("cover_seed", g.coverSeed)
                put("tags", JSONArray().apply { g.tags.forEach { put(it) } })
                put("cost_breakdown", JSONArray().apply {
                    g.costBreakdown.forEach { c ->
                        put(JSONObject().put("label", c.label).put("amount", c.amount).put("total", c.total))
                    }
                })
                put("tips", JSONArray().apply { g.tips.forEach { put(it) } })
                put("itinerary", JSONArray().apply {
                    g.itinerary.forEach { d ->
                        put(JSONObject().apply {
                            put("day", d.day)
                            put("title", d.title)
                            put("special", d.special)
                            put("special_reason", d.specialReason ?: "")
                            put("spots", JSONArray().apply {
                                d.spots.forEach { s ->
                                    put(JSONObject().apply {
                                        put("name", s.name)
                                        put("note", s.note)
                                        put("cost", s.cost)
                                        put("tips", s.tips ?: "")
                                        put("seed", s.seed)
                                    })
                                }
                            })
                        })
                    }
                })
            }
            JSONObject(request("POST", "/guides", body.toString())).getInt("id")
        }.getOrNull()
    }

    /** 点赞/取消点赞；返回 (likes, liked)，失败 null */
    suspend fun toggleGuideLike(guideId: Int): Pair<Int, Boolean>? = withContext(Dispatchers.IO) {
        runCatching {
            val data = JSONObject(request("POST", "/guides/$guideId/like")).getJSONObject("data")
            data.optInt("likes") to data.optBoolean("liked")
        }.getOrNull()
    }

    /** 收藏/取消收藏；返回 (favs, favored)，失败 null */
    suspend fun toggleGuideFav(guideId: Int): Pair<Int, Boolean>? = withContext(Dispatchers.IO) {
        runCatching {
            val data = JSONObject(request("POST", "/guides/$guideId/fav")).getJSONObject("data")
            val favAny = data.opt("favored")
            val favored = favAny as? Boolean ?: (favAny as? Number)?.toInt()?.let { it > 0 } ?: false
            data.optInt("favs", data.optInt("favored")) to favored
        }.getOrNull()
    }

    // ---------- 评论 ----------

    suspend fun fetchComments(guideId: Int): List<Comment>? = withContext(Dispatchers.IO) {
        runCatching {
            val arr = JSONArray(request("GET", "/guides/$guideId/comments", auth = false))
            (0 until arr.length()).map { parseComment(arr.getJSONObject(it)) }
        }.getOrNull()
    }

    /** 发表评论或楼中楼回复（parentId != null 为回复）；返回新评论 id */
    suspend fun postComment(guideId: Int, content: String, parentId: Int? = null): Int? =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("guide_id", guideId)
                    put("content", content)
                    parentId?.let { put("parent_id", it) }
                }
                JSONObject(request("POST", "/comments", body.toString())).getInt("id")
            }.getOrNull()
        }

    // ---------- 足迹 / 路线 ----------

    suspend fun fetchMyRoutes(): List<Route>? = withContext(Dispatchers.IO) {
        runCatching {
            val arr = JSONArray(request("GET", "/routes/me"))
            (0 until arr.length()).map { parseRoute(arr.getJSONObject(it)) }
        }.getOrNull()
    }

    suspend fun createRoute(r: Route): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("title", r.title)
                put("cities", JSONArray().apply { r.cities.forEach { put(it) } })
                put("date", r.date)
                put("note", r.note)
                put("cover_seed", r.coverSeed)
                put("year", r.year)
                put("special", r.special)
                put("special_reason", r.specialReason ?: "")
                put("tags", JSONArray().apply { r.tags.forEach { put(it) } })
                put("spots", JSONArray().apply {
                    r.itinerary.flatMap { it.spots }.forEach { s ->
                        put(JSONObject().apply {
                            put("name", s.name)
                            put("note", s.note)
                            put("cost", s.cost)
                            put("tips", s.tips ?: "")
                            put("seed", s.seed)
                        })
                    }
                })
            }
            JSONObject(request("POST", "/routes", body.toString())).getInt("id")
        }.getOrNull()
    }

    // ---------- 用户数据同步（/api/sync/*） ----------

    /** 媒体上传结果（对应服务端 /api/sync/media 的返回） */
    data class MediaRef(
        val url: String,
        val sha256: String,
        val bytes: Long,
        val mime: String,
        val dedup: Boolean
    )

    /** 批量上传服务端返回的四类计数；不能只看 HTTP 200 */
    data class SyncCounts(
        val created: Int = 0,
        val updated: Int = 0,
        val unchanged: Int = 0,
        val deleted: Int = 0
    )

    /** 从批量上传返回 root 解析计数；缺失字段按 0 计 */
    fun parseSyncCounts(root: JSONObject?): SyncCounts = root?.let {
        SyncCounts(
            created = it.optInt("created", 0),
            updated = it.optInt("updated", 0),
            unchanged = it.optInt("unchanged", 0),
            deleted = it.optInt("deleted", 0)
        )
    } ?: SyncCounts()

    /** 服务端同步水位（有条数、配额，用于设置页展示） */
    suspend fun syncStatus(): JSONObject? = withContext(Dispatchers.IO) {
        runCatching { JSONObject(request("GET", "/api/sync/status")) }.getOrNull()
    }

    /** 批量上传播足迹（按 client_id 幂等，updated_at 大者胜） */
    suspend fun uploadFootprints(
        items: List<JSONObject>,
        deletedClientIds: List<Int> = emptyList()
    ): JSONObject? = uploadSyncBatch(
        path = "/api/sync/footprints",
        itemKey = "items",
        items = items,
        deletedKey = "deleted_client_ids",
        deleted = deletedClientIds
    )

    /** 批量上传行程 */
    suspend fun uploadRoutes(
        items: List<JSONObject>,
        deletedClientIds: List<Int> = emptyList()
    ): JSONObject? = uploadSyncBatch(
        path = "/api/sync/routes",
        itemKey = "items",
        items = items,
        deletedKey = "deleted_client_ids",
        deleted = deletedClientIds
    )

    /** 上传设置快照（payload 任意 JSON，服务端限 256KB） */
    suspend fun uploadSettings(payload: JSONObject, updatedAt: Long): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("payload", payload)
                    put("updated_at", updatedAt)
                }
                request("PUT", "/api/sync/settings", body.toString())
                true
            }.getOrDefault(false)
        }

    /** 上传单个媒体文件（照片/视频），服务端按 sha256 去重 */
    suspend fun uploadMedia(file: File, mime: String): MediaRef? = withContext(Dispatchers.IO) {
        runCatching {
            val name = file.name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
            val o = JSONObject(
                multipartUploadFile("/api/sync/media", "file", name, file, mime, readTimeoutMs = 180_000)
            )
            MediaRef(
                url = o.getString("url"),
                sha256 = o.optString("sha256"),
                bytes = o.optLong("bytes"),
                mime = o.optString("mime"),
                dedup = o.optBoolean("dedup")
            )
        }.getOrNull()
    }

    /** 删除服务端一条足迹（通常不用，批量删除走 deleted_client_ids） */
    suspend fun deleteSyncedFootprint(clientId: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            request("DELETE", "/api/sync/footprints/$clientId")
            true
        }.getOrDefault(false)
    }

    // ---------- 下行回拉（增量水位 / 换机恢复） ----------

    /** 增量回拉一条同步分区；每项含 client_id / updated_at / payload，兼容 items 与 data.items */
    private suspend fun fetchSyncBatch(path: String, since: Long, limit: Int): JSONArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = JSONObject(request("GET", "$path?since=$since&limit=$limit"))
                root.optJSONArray("items")
                    ?: root.optJSONObject("data")?.optJSONArray("items")
                    ?: JSONArray()
            }.getOrNull()
        }

    /** 增量回拉足迹；since 为本地已同步水位（毫秒），单批上限 500 */
    suspend fun fetchSyncFootprints(since: Long, limit: Int = 500): JSONArray? =
        fetchSyncBatch("/api/sync/footprints", since, limit)

    /** 增量回拉行程 */
    suspend fun fetchSyncRoutes(since: Long, limit: Int = 500): JSONArray? =
        fetchSyncBatch("/api/sync/routes", since, limit)

    /** 删除服务端一条行程 */
    suspend fun deleteSyncedRoute(clientId: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching { request("DELETE", "/api/sync/routes/$clientId"); true }.getOrDefault(false)
    }

    /** 读取设置快照 payload（不含凭证），失败返回 null */
    suspend fun fetchSyncSettings(): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(request("GET", "/api/sync/settings"))
            root.optJSONObject("payload") ?: root.optJSONObject("data")?.optJSONObject("payload") ?: root
        }.getOrNull()
    }

    /** 读取服务端聚合的全量快照 */
    suspend fun fetchSnapshot(): JSONObject? = withContext(Dispatchers.IO) {
        runCatching { JSONObject(request("GET", "/api/sync/snapshot")) }.getOrNull()
    }

    /**
     * 登录后全量恢复：一次取回云端所有个人数据
     * （资料/设置/足迹/行程/分区/媒体清单/隐私密文清单/AI 端点/攻略评论）。
     * 新设备或重装后登录时调用，失败返回 null，由上层保留本机数据。
     */
    suspend fun fetchBootstrap(): JSONObject? = withContext(Dispatchers.IO) {
        runCatching { JSONObject(request("GET", "/api/sync/bootstrap?limit=5000")) }.getOrNull()
    }

    /** 读取我的 AI 端点配置（含解密后的 Key，仅登录本人可见） */
    suspend fun fetchAiConfig(): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(request("GET", "/api/ai/config"))
            root.optJSONObject("data") ?: root
        }.getOrNull()
    }

    /** 保存 / 更新我的 AI 端点配置；服务端加密落库，推理仍在手机直连 */
    suspend fun uploadAiConfig(doc: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(request("PUT", "/api/ai/config", doc.toString()))
            root.optJSONObject("data") ?: root
        }.getOrNull()
    }

    /** 提交全量快照（换机 / 恢复前整包快传） */
    suspend fun postSnapshot(doc: JSONObject): Boolean = withContext(Dispatchers.IO) {
        runCatching { request("POST", "/api/sync/snapshot", doc.toString()); true }.getOrDefault(false)
    }

    /** 读取自定义分区（music/comments/plans/vault/notes/watch/custom） */
    suspend fun fetchCollection(kind: String): JSONArray? = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(request("GET", "/api/sync/collections/$kind"))
            root.optJSONArray("items") ?: root.optJSONObject("data")?.optJSONArray("items") ?: JSONArray()
        }.getOrNull()
    }

    /**
     * 批量写自定义分区（增量 upsert + 显式删除清单）。
     * 服务端按 (user, kind, client_id) 幂等覆盖，updated_at 小者不覆盖；
     * deletedClientIds 用于本地删除后同步删除云端，避免「删了又回来」。
     */
    suspend fun putCollection(
        kind: String,
        items: JSONArray,
        deletedClientIds: List<String> = emptyList(),
        updatedAt: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("items", items)
                put("deleted_client_ids", JSONArray(deletedClientIds))
                put("updated_at", updatedAt)
            }
            request("PUT", "/api/sync/collections/$kind", body.toString())
            true
        }.getOrDefault(false)
    }

    /** 读取隐私空间密文块索引（端侧解密，服务端不解密） */
    suspend fun fetchVault(): JSONArray? = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(request("GET", "/api/sync/vault"))
            root.optJSONArray("items") ?: root.optJSONObject("data")?.optJSONArray("items") ?: JSONArray()
        }.getOrNull()
    }

    /** 读取全量档案元信息（清单 / 计数） */
    suspend fun fetchArchive(): JSONObject? = withContext(Dispatchers.IO) {
        runCatching { JSONObject(request("GET", "/api/sync/archive")) }.getOrNull()
    }

    /** 读取全量档案正文（换机恢复用，返回原始 JSON 文本，解析交给上层） */
    suspend fun fetchArchiveContent(): String? = withContext(Dispatchers.IO) {
        runCatching { request("GET", "/api/sync/archive/content") }.getOrNull()
    }

    /** 上传全量档案：一次把所有分区（足迹/行程/计划/音乐/评论/隐私空间索引/设置）推到服务端 */
    suspend fun uploadArchive(doc: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        runCatching { JSONObject(request("POST", "/api/sync/archive", doc.toString())) }.getOrNull()
    }

    /**
     * 上传隐私空间密文块（附带 item_id / name / kind 表单字段）。
     * 服务端与后台都不持有密钥，只能存原始密文，不会尝试解密。
     */
    suspend fun uploadVaultFile(file: File, itemId: String, name: String, kind: String): JSONObject? =
        withContext(Dispatchers.IO) {
            runCatching {
                JSONObject(
                    multipartUploadFileWithFields(
                        path = "/api/sync/vault",
                        field = "file",
                        filename = file.name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80),
                        file = file,
                        mime = "application/octet-stream",
                        fields = mapOf("item_id" to itemId, "name" to name, "kind" to kind),
                        readTimeoutMs = 120_000
                    )
                )
            }.getOrNull()
        }

    private suspend fun uploadSyncBatch(
        path: String,
        itemKey: String,
        items: List<JSONObject>,
        deletedKey: String,
        deleted: List<Int>
    ): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put(itemKey, JSONArray().apply { items.forEach { put(it) } })
                put(deletedKey, JSONArray().apply { deleted.forEach { put(it) } })
            }
            JSONObject(request("PUT", path, body.toString()))
        }.getOrNull()
    }

    // ============ 内部：HTTP ============

    private fun request(
        method: String,
        path: String,
        body: String? = null,
        contentType: String = "application/json; charset=utf-8",
        auth: Boolean = true
    ): String {
        // 纯本地模式：所有网络出口在这里一刀短路。调用方的 runCatching/try 会落到 null/false 降级分支，
        // 不会真的发请求，也就不会有 10 秒超时把启动拖住。
        if (!CloudConfig.ENABLED) error("CLOUD_DISABLED")
        val conn = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = method
            setRequestProperty("Accept", "application/json")
            if (auth) token?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType)
            }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code == 401 && auth && token != null) onUnauthorized?.invoke()
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) error("HTTP $code: $text")
            return text
        } finally {
            conn.disconnect()
        }
    }

    /** 手动拼 multipart/form-data 上传（不引第三方库） */
    private fun multipartUpload(path: String, field: String, filename: String, bytes: ByteArray): String {
        if (!CloudConfig.ENABLED) error("CLOUD_DISABLED")
        val boundary = "----MyZujiBoundary" + UUID.randomUUID().toString().replace("-", "")
        val conn = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        try {
            DataOutputStream(conn.outputStream).use { out ->
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"$field\"; filename=\"$filename\"\r\n")
                out.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                out.write(bytes)
                out.writeBytes("\r\n--$boundary--\r\n")
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) error("HTTP $code: $text")
            return text
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 文件流式 multipart 上传（照片可能几十 MB、视频可能几百 MB，不能整块读进内存）。
     * [readTimeoutMs] 单独放宽：大文件上传耗时远大于普通接口的 10 秒。
     */
    private fun multipartUploadFile(
        path: String,
        field: String,
        filename: String,
        file: File,
        mime: String,
        readTimeoutMs: Int = 60_000
    ): String {
        if (!CloudConfig.ENABLED) error("CLOUD_DISABLED")
        val boundary = "----MyZujiBoundary" + UUID.randomUUID().toString().replace("-", "")
        val conn = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = readTimeoutMs
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        try {
            DataOutputStream(conn.outputStream).use { out ->
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"$field\"; filename=\"$filename\"\r\n")
                out.writeBytes("Content-Type: $mime\r\n\r\n")
                out.flush()
                file.inputStream().buffered().use { it.copyTo(out) }
                out.writeBytes("\r\n--$boundary--\r\n")
                out.flush()
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) error("HTTP $code: $text")
            return text
        } finally {
            conn.disconnect()
        }
    }

    // ============ 内部：JSON → 模型 ============

    /**
     * 文件 multipart 上传 + 额外文本字段（隐私空间密文要用 item_id/name/kind）。
     * 文本字段按 UTF-8 写字节，中文文件名不会变成乱码。
     */
    private fun multipartUploadFileWithFields(
        path: String,
        field: String,
        filename: String,
        file: File,
        mime: String,
        fields: Map<String, String>,
        readTimeoutMs: Int = 60_000
    ): String {
        if (!CloudConfig.ENABLED) error("CLOUD_DISABLED")
        val boundary = "----MyZujiBoundary" + UUID.randomUUID().toString().replace("-", "")
        val conn = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = readTimeoutMs
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        try {
            DataOutputStream(conn.outputStream).use { out ->
                fields.forEach { (k, v) ->
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"$k\"\r\n\r\n")
                    out.write(v.toByteArray(Charsets.UTF_8))
                    out.writeBytes("\r\n")
                }
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"$field\"; filename=\"$filename\"\r\n")
                out.writeBytes("Content-Type: $mime\r\n\r\n")
                out.flush()
                file.inputStream().buffered().use { it.copyTo(out) }
                out.writeBytes("\r\n--$boundary--\r\n")
                out.flush()
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) error("HTTP $code: $text")
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun parseRemoteUser(o: JSONObject) = RemoteUser(
        id = o.getInt("id"),
        nickname = o.optString("nickname", "旅行者"),
        signature = o.optString("signature", ""),
        avatar = o.optString("avatar").ifBlank { null },
        citiesCount = o.optInt("cities_count"),
        footprintCount = o.optInt("footprint_count"),
        daysCount = o.optInt("days_count"),
        followers = o.optInt("followers"),
        following = o.optInt("following"),
        likesReceived = o.optInt("likes_received"),
        defaultPublic = o.optBoolean("default_public", true),
        notifEnabled = o.optBoolean("notif_enabled", true),
        shareEnabled = o.optBoolean("share_enabled", true),
        username = o.optString("username"),
        // created_at 形如 2026-10-27T23:46:01，只取日期部分
        createdAt = o.optString("created_at").take(10).ifBlank { null }
    )

    private fun parseGuide(o: JSONObject): Guide {
        val tags = strList(o.optJSONArray("tags"))
        return Guide(
            id = o.getInt("id"),
            title = o.optString("title"),
            city = o.optString("city"),
            days = o.optInt("days", 1),
            type = tags.firstOrNull() ?: "城市漫游",
            author = o.optString("author_name", "旅行者"),
            authorIntro = "",
            likes = o.optInt("likes"),
            favs = o.optInt("favored"),
            cost = o.optString("cost"),
            coverSeed = o.optInt("cover_seed"),
            tags = tags,
            intro = o.optString("intro"),
            costBreakdown = costList(o.optJSONArray("cost_breakdown")),
            tips = strList(o.optJSONArray("tips")),
            itinerary = dayPlanList(o.optJSONArray("itinerary")),
            commentList = emptyList(),
            featured = o.optBoolean("featured"),
            theme = o.optString("theme"),
            viewedCount = o.optInt("viewed_count"),
            authorId = o.optInt("author_id", -1)
        )
    }

    private fun parseRoute(o: JSONObject): Route {
        val spots = spotList(o.optJSONArray("spots"))
        return Route(
            id = o.getInt("id"),
            title = o.optString("title"),
            year = o.optInt("year"),
            date = o.optString("date"),
            days = 1,
            cities = strList(o.optJSONArray("cities")),
            coverSeed = o.optInt("cover_seed"),
            special = o.optBoolean("special"),
            specialReason = o.optString("special_reason").ifBlank { null },
            note = o.optString("note"),
            tags = strList(o.optJSONArray("tags")),
            totalCost = "",
            isPublic = true,
            photoCount = 0,
            itinerary = if (spots.isEmpty()) emptyList() else listOf(DayPlan(1, o.optString("title"), spots))
        )
    }

    private fun parseComment(o: JSONObject): Comment {
        val repliesArr = o.optJSONArray("replies")
        val replies = if (repliesArr == null) emptyList() else (0 until repliesArr.length()).map { i ->
            val r = repliesArr.getJSONObject(i)
            CommentReply(
                user = r.optString("user_name", "旅行者"),
                time = relativeTime(r.optString("created_at")),
                content = r.optString("content"),
                likes = r.optInt("likes"),
                id = r.getInt("id")
            )
        }
        return Comment(
            user = o.optString("user_name", "旅行者"),
            time = relativeTime(o.optString("created_at")),
            content = o.optString("content"),
            likes = o.optInt("likes"),
            replies = replies,
            id = o.getInt("id")
        )
    }

    private fun spotList(arr: JSONArray?): List<Spot> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val s = arr.getJSONObject(i)
            Spot(
                name = s.optString("name"),
                note = s.optString("note"),
                cost = s.optString("cost"),
                tips = s.optString("tips").ifBlank { null },
                seed = s.optInt("seed")
            )
        }
    }

    private fun dayPlanList(arr: JSONArray?): List<DayPlan> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val d = arr.getJSONObject(i)
            DayPlan(
                day = d.optInt("day", i + 1),
                title = d.optString("title"),
                spots = spotList(d.optJSONArray("spots")),
                special = d.optBoolean("special"),
                specialReason = d.optString("special_reason").ifBlank { null }
            )
        }
    }

    private fun costList(arr: JSONArray?): List<CostItem> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            CostItem(
                label = c.optString("label"),
                amount = c.optString("amount"),
                total = c.optBoolean("total")
            )
        }
    }

    private fun strList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
    }

    /** ISO 时间 → 中文相对时间 */
    private fun relativeTime(iso: String): String {
        if (iso.isBlank()) return ""
        val parsed = runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(iso.take(19))
        }.getOrNull() ?: return iso.take(10)
        val diff = System.currentTimeMillis() - parsed.time
        val min = diff / 60_000
        val hour = diff / 3_600_000
        val day = diff / 86_400_000
        return when {
            min < 1 -> "刚刚"
            min < 60 -> "${min}分钟前"
            hour < 24 -> "${hour}小时前"
            day < 30 -> "${day}天前"
            else -> iso.take(10)
        }
    }
}
