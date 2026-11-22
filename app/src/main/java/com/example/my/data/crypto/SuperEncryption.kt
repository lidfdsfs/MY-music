package com.example.my.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.example.my.data.DmMessage
import com.example.my.data.UserScope
import org.bouncycastle.crypto.digests.Blake3Digest
import org.bouncycastle.crypto.modes.GCMSIVBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.KeyStore

/**
 * 超级加密通信（端到端加密）的工程实现。
 *
 * 协议版本 v1：
 * - 身份：Ed25519 签名密钥 + X25519 长期协商密钥；私钥只在本机，使用 Android Keystore 的
 *   AES-GCM 密钥包裹后落盘，服务端和备份里都拿不到明文私钥。
 * - 每条消息：临时 X25519 密钥 + 双方长期 X25519 做两次 ECDH，HKDF-SHA256 派生消息密钥；
 *   AES-256-GCM-SIV 认证加密，AD 绑定会话、消息 id、时间戳与双方公钥。
 * - 安全码：双方 Ed25519 公钥排序后做 BLAKE3，取 60 bit 分组展示，用于当面/视频核对。
 * - 握手：双方先在私信里交换公钥（只交换公钥，不交换任何聊天内容）；之后正文全部是密文。
 * - 服务器只看到 body="🔒" 和 extra.e2ee 里的不透明信封，无法解密正文。
 *
 * 说明：这是可落地的 v1 核心层（第 2/3/6/7/9/10 层的裁剪版）。完整方案中的 ML-KEM-768、
 * Shamir(3,4)、Argon2id 口令拉伸与密封发件人需要服务端配合或额外密钥恢复 UX，暂未启用，
 * 详见 docs/超级加密通信实现说明.md。
 */
object SuperEncryption {

    private const val TAG = "SuperEncryption"
    const val PREFIX = "MYE2EE1:"
    const val LOCK_BODY = "🔒"
    const val DECRYPT_FAILED = "这条加密消息暂时无法显示"

    private const val PREFS = "super_encryption"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_IDENTITY = "identity_blob"
    private const val KEY_HS_SENT = "hs_sent_at_"
    private const val KEY_PEER_IPK = "peer_ipk_"
    private const val KEY_PEER_SPK = "peer_spk_"
    private const val KEY_PEER_CONFLICT = "peer_conflict_"
    private const val KEYSTORE_ALIAS_PREFIX = "myzuji_super_e2ee_"
    private const val ALG = "X25519+Ed25519+AES-256-GCM-SIV"

    private val rnd = SecureRandom()

    private data class Identity(
        val xPriv: ByteArray,
        val xPub: ByteArray,
        val edPriv: ByteArray,
        val edPub: ByteArray
    ) {
        fun clear() {
            Arrays.fill(xPriv, 0)
            Arrays.fill(edPriv, 0)
        }
    }

    private data class PeerKey(val ipk: ByteArray, val spk: ByteArray)

    private fun prefs(context: Context) =
        UserScope.prefs(context.applicationContext, PREFS)

    // ---------------- 开关与密钥生命周期 ----------------

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    /** 开启；首次开启会生成身份密钥并跑一遍本地加解密自检。 */
    fun enable(context: Context): Boolean {
        if (UserScope.uid <= 0) return false
        val ok = runCatching {
            ensureIdentity(context)
            val test = selfTest()
            if (!test) return@runCatching false
            prefs(context).edit().putBoolean(KEY_ENABLED, true).commit()
            true
        }.getOrElse {
            Log.w(TAG, "enable failed", it)
            false
        }
        Log.i(TAG, "enable=$ok selfTest=$ok")
        return ok
    }

    /** 关闭只停止后续加密，不删除密钥；否则历史密文将永久无法解开。 */
    fun disable(context: Context) {
        prefs(context).edit().putBoolean(KEY_ENABLED, false).apply()
    }

    fun hasIdentity(context: Context): Boolean = loadIdentity(context) != null

    // ---------------- 握手 ----------------

    fun handshakeEnvelope(context: Context): String? = runCatching {
        val id = ensureIdentity(context) ?: return@runCatching null
        try {
            val ts = System.currentTimeMillis() / 1000
            val ipk = b64(id.edPub)
            val spk = b64(id.xPub)
            val canonical = "hs|1|$ts|$ipk|$spk"
            val sig = sign(id.edPriv, canonical)
            PREFIX + JSONObject()
                .put("t", "hs")
                .put("v", 1)
                .put("alg", ALG)
                .put("ts", ts)
                .put("ipk", ipk)
                .put("spk", spk)
                .put("sig", b64(sig))
                .toString()
        } finally {
            id.clear()
        }
    }.getOrNull()

    fun isHandshake(envelope: String?): Boolean = runCatching {
        envelope != null && JSONObject(strip(envelope)).optString("t") == "hs"
    }.getOrDefault(false)

    fun isEncryptedBody(body: String?): Boolean =
        body == LOCK_BODY || (body != null && body.startsWith(PREFIX))

    /** 从任意收到/拉到的信封里学习对方公钥；同一 peer 首次信任（TOFU）。 */
    fun learnPeer(context: Context, peerId: Int, envelope: String?): Boolean {
        sanitizeSelfLearnedPeer(context, peerId)
        val obj = runCatching { JSONObject(strip(envelope ?: return false)) }.getOrNull()
            ?: return false
        val ipk = obj.optString("ipk").takeIf { it.isNotBlank() } ?: return false
        val spk = obj.optString("spk").takeIf { it.isNotBlank() } ?: return false
        val p = prefs(context)
        val oldIpk = p.getString(KEY_PEER_IPK + peerId, null)
        val oldSpk = p.getString(KEY_PEER_SPK + peerId, null)
        if (oldIpk != null && oldIpk != ipk) {
            p.edit().putBoolean(KEY_PEER_CONFLICT + peerId, true).apply()
            return false
        }
        if (oldSpk != null && oldSpk != spk) {
            p.edit().putBoolean(KEY_PEER_CONFLICT + peerId, true).apply()
            return false
        }
        p.edit()
            .putString(KEY_PEER_IPK + peerId, ipk)
            .putString(KEY_PEER_SPK + peerId, spk)
            .putBoolean(KEY_PEER_CONFLICT + peerId, false)
            .apply()
        return true
    }

    fun peerKeyKnown(context: Context, peerId: Int): Boolean {
        sanitizeSelfLearnedPeer(context, peerId)
        val p = prefs(context)
        return p.getString(KEY_PEER_IPK + peerId, null) != null &&
            p.getString(KEY_PEER_SPK + peerId, null) != null &&
            !p.getBoolean(KEY_PEER_CONFLICT + peerId, false)
    }

    fun peerKeyConflict(context: Context, peerId: Int): Boolean =
        prefs(context).getBoolean(KEY_PEER_CONFLICT + peerId, false)

    fun handshakeSentRecently(context: Context, peerId: Int, windowMs: Long = 10 * 60_000L): Boolean {
        val at = prefs(context).getLong(KEY_HS_SENT + peerId, 0L)
        return at > 0 && System.currentTimeMillis() - at < windowMs
    }

    fun markHandshakeSent(context: Context, peerId: Int) {
        prefs(context).edit().putLong(KEY_HS_SENT + peerId, System.currentTimeMillis()).apply()
    }

    /** 打开聊天时调用：从整段消息里学习所有握手/密文信封。自己发的握手不能当对方公钥。 */
    fun learnFromMessages(context: Context, peerId: Int, messages: List<DmMessage>) {
        messages.forEach { m ->
            if (m.mine) return@forEach
            val e = m.e2eeEnvelope ?: return@forEach
            if (isHandshake(e)) learnPeer(context, peerId, e)
        }
    }

    /** 老版本可能把自己发的握手学成了对方公钥；发现 peer 公钥等于自己的公钥时清掉，等待真实握手。 */
    private fun sanitizeSelfLearnedPeer(context: Context, peerId: Int) {
        val id = loadIdentity(context) ?: return
        try {
            val own = b64(id.edPub)
            val p = prefs(context)
            if (p.getString(KEY_PEER_IPK + peerId, null) == own) {
                p.edit()
                    .remove(KEY_PEER_IPK + peerId)
                    .remove(KEY_PEER_SPK + peerId)
                    .putBoolean(KEY_PEER_CONFLICT + peerId, false)
                    .apply()
            }
        } finally {
            id.clear()
        }
    }

    // ---------------- 加密 / 解密 ----------------

    fun encrypt(context: Context, conversationId: Int, peerId: Int, plaintext: String): String? {
        sanitizeSelfLearnedPeer(context, peerId)
        val id = loadIdentity(context) ?: return null
        try {
            val peer = loadPeer(context, peerId) ?: return null
            if (peerKeyConflict(context, peerId)) return null
            return encryptEnvelope(id, conversationId, peer.spk, plaintext)
        } catch (t: Throwable) {
            Log.w(TAG, "encrypt failed", t)
            return null
        } finally {
            id.clear()
        }
    }

    fun decrypt(context: Context, conversationId: Int, peerId: Int, envelope: String?): String? {
        if (envelope.isNullOrBlank()) return null
        val id = loadIdentity(context) ?: return null
        try {
            val obj = JSONObject(strip(envelope))
            if (obj.optString("t") != "msg") return null
            if (!verifyMessage(obj)) return null
            // 验签通过后才信任公钥，避免伪造公钥污染 TOFU 记录
            learnPeer(context, peerId, envelope)
            if (peerKeyConflict(context, peerId)) return null
            return decryptMessage(id, conversationId, obj)
        } catch (t: Throwable) {
            Log.w(TAG, "decrypt failed", t)
            return null
        } finally {
            id.clear()
        }
    }

    /**
     * 把服务端消息转换成 UI 可直接显示的数据：body 保持原样（复制/引用仍能拿到），
     * displayBody/displayReplyToBody 放解密结果；解密失败统一显示错误，不抛异常。
     */
    fun decorate(
        context: Context,
        conversationId: Int,
        peerId: Int,
        messages: List<DmMessage>
    ): List<DmMessage> {
        // 开关只控制“新消息是否加密发送”，不能控制“本地能不能看历史消息”。
        // 否则用户一关开关，服务端的 🔒 就会被原样显示出来，等于把密文暴露给用户。
        val id = loadIdentity(context) ?: return messages.map { decryptFailedIfNeeded(it) }
        try {
            val out = messages.map { m ->
                val env = m.e2eeEnvelope
                val sentPlain = if (m.mine) cachedSentPlain(context, conversationId, m.id) else null
                if (!sentPlain.isNullOrBlank()) {
                    m.copy(displayBody = sentPlain)
                } else if (env.isNullOrBlank()) {
                    decryptFailedIfNeeded(m)
                } else if (isHandshake(env)) {
                    if (!m.mine) learnPeer(context, peerId, env)
                    m.copy(e2eeHandshake = true, displayBody = "")
                } else {
                    val obj = runCatching { JSONObject(strip(env)) }.getOrNull()
                    val ok = obj != null && verifyMessage(obj)
                    if (!ok) {
                        m.copy(displayBody = if (m.mine) "消息已发送" else DECRYPT_FAILED)
                    } else {
                        if (!m.mine) learnPeer(context, peerId, env)
                        val text = runCatching { decryptMessage(id, conversationId, obj!!) }.getOrNull()
                        m.copy(displayBody = text ?: if (m.mine) "消息已发送" else DECRYPT_FAILED)
                    }
                }
            }
            // 会话列表只能拿到服务端的 last_preview（固定 🔒）。这里把最近一条能解开的明文
            // 缓存在本机，列表就能显示正常预览，而不是把 🔒 或“超级加密消息”摆给用户。
            out.lastOrNull { !it.e2eeHandshake && !it.displayBody.isNullOrBlank() }
                ?.displayBody
                ?.takeIf { it != DECRYPT_FAILED }
                ?.let { saveCachedPreview(context, conversationId, it) }
            return out
        } finally {
            id.clear()
        }
    }

    fun saveCachedPreview(context: Context, conversationId: Int, text: String) {
        prefs(context).edit().putString("preview_$conversationId", text.take(120)).apply()
    }

    fun cachedPreview(context: Context, conversationId: Int): String? =
        prefs(context).getString("preview_$conversationId", null)?.takeIf { it.isNotBlank() }

    fun saveSentPlain(context: Context, conversationId: Int, messageId: Int, text: String) {
        if (messageId <= 0) return
        prefs(context).edit().putString("sent_${conversationId}_$messageId", text).apply()
    }

    fun cachedSentPlain(context: Context, conversationId: Int, messageId: Int): String? =
        prefs(context).getString("sent_${conversationId}_$messageId", null)?.takeIf { it.isNotBlank() }

    fun decorateOne(
        context: Context,
        conversationId: Int,
        peerId: Int,
        message: DmMessage
    ): DmMessage {
        return decorate(context, conversationId, peerId, listOf(message)).firstOrNull() ?: message
    }

    private fun decryptFailedIfNeeded(m: DmMessage): DmMessage = when {
        m.e2eeEnvelope != null -> m.copy(
            displayBody = if (m.mine) "消息已发送" else DECRYPT_FAILED
        )
        // 服务端只给了 🔒 却没有信封（老消息/被清理过的 extra）：也不要让用户看到密文占位
        m.body == LOCK_BODY -> m.copy(displayBody = "加密消息（本机没有可用的密钥信封）")
        else -> m
    }

    // ---------------- 安全码 ----------------

    /** 我的公钥指纹（本机展示用）。 */
    fun mySafetyCode(context: Context): String {
        val id = loadIdentity(context) ?: return "未生成密钥"
        return try {
            fingerprint(listOf(id.edPub))
        } finally {
            id.clear()
        }
    }

    /** 双方安全码：双方 Ed25519 公钥排序后 BLAKE3，两人算出来必须一致。 */
    fun safetyCode(context: Context, peerId: Int): String? {
        sanitizeSelfLearnedPeer(context, peerId)
        val id = loadIdentity(context) ?: return null
        val peer = loadPeer(context, peerId) ?: return null
        return try {
            fingerprint(listOf(id.edPub, peer.ipk))
        } finally {
            id.clear()
        }
    }

    /** 聊天页顶部状态文案；未开启时返回 null。 */
    fun chatStatus(context: Context, peerId: Int): String? {
        if (!isEnabled(context)) return null
        if (peerKeyConflict(context, peerId)) return "🔒 安全码已变化，请先核对联系人"
        if (!peerKeyKnown(context, peerId)) return "🔒 超级加密已开启 · 等待对方握手"
        val code = safetyCode(context, peerId) ?: return "🔒 超级加密已开启"
        return "🔒 端到端加密 · 安全码 $code"
    }

    // ---------------- 本地自检 ----------------

    /** 两套临时身份跑完整“加密→验签→解密”链路；任何异常都返回 false。 */
    fun selfTest(): Boolean {
        val a = generateIdentity()
        val b = generateIdentity()
        return try {
            val env = encryptEnvelope(a, 7, b.xPub, "超级加密通信自检")
            val out = decryptEnvelope(b, 7, env)
            out == "超级加密通信自检"
        } catch (t: Throwable) {
            Log.w(TAG, "selfTest failed", t)
            false
        } finally {
            a.clear()
            b.clear()
        }
    }

    // ---------------- 协议实现 ----------------

    private fun ensureIdentity(context: Context): Identity? {
        loadIdentity(context)?.let { return it }
        val id = generateIdentity()
        return runCatching {
            saveIdentity(context, id)
            id
        }.getOrElse {
            Log.w(TAG, "save identity failed", it)
            id.clear()
            null
        }
    }

    private fun loadIdentity(context: Context): Identity? {
        val sealed = prefs(context).getString(KEY_IDENTITY, null) ?: return null
        val raw = runCatching { unseal(context, sealed) }.getOrNull() ?: return null
        if (raw.size != 64) {
            Arrays.fill(raw, 0)
            return null
        }
        return runCatching {
            val xp = X25519PrivateKeyParameters(raw, 0)
            val ep = Ed25519PrivateKeyParameters(raw, 32)
            Identity(
                xPriv = raw.copyOfRange(0, 32),
                xPub = xp.generatePublicKey().encoded,
                edPriv = raw.copyOfRange(32, 64),
                edPub = ep.generatePublicKey().encoded
            ).also { Arrays.fill(raw, 0) }
        }.getOrElse {
            Arrays.fill(raw, 0)
            null
        }
    }

    private fun saveIdentity(context: Context, id: Identity) {
        val raw = ByteArray(64)
        System.arraycopy(id.xPriv, 0, raw, 0, 32)
        System.arraycopy(id.edPriv, 0, raw, 32, 32)
        val sealed = seal(context, raw)
        Arrays.fill(raw, 0)
        prefs(context).edit().putString(KEY_IDENTITY, sealed).commit()
    }

    private fun generateIdentity(): Identity {
        val x = X25519PrivateKeyParameters(rnd)
        val e = Ed25519PrivateKeyParameters(rnd)
        return Identity(
            xPriv = x.encoded,
            xPub = x.generatePublicKey().encoded,
            edPriv = e.encoded,
            edPub = e.generatePublicKey().encoded
        )
    }

    private fun loadPeer(context: Context, peerId: Int): PeerKey? {
        val p = prefs(context)
        val ipk = p.getString(KEY_PEER_IPK + peerId, null) ?: return null
        val spk = p.getString(KEY_PEER_SPK + peerId, null) ?: return null
        return runCatching { PeerKey(b64d(ipk), b64d(spk)) }.getOrNull()
    }

    private fun encryptEnvelope(
        sender: Identity,
        conversationId: Int,
        peerSpk: ByteArray,
        plaintext: String
    ): String {
        val ephemeral = X25519PrivateKeyParameters(rnd)
        val epk = ephemeral.generatePublicKey().encoded
        val staticShared = agree(sender.xPriv, peerSpk)
        val ephShared = agree(ephemeral.encoded, peerSpk)
        val mid = UUID.randomUUID().toString()
        val ts = System.currentTimeMillis() / 1000
        val key = hkdf(
            staticShared + ephShared,
            "MYE2EE|v1|$conversationId".toByteArray(StandardCharsets.UTF_8),
            "MYE2EE|v1|msg|$mid|$conversationId".toByteArray(StandardCharsets.UTF_8),
            32
        )
        val nonce = ByteArray(12).also { rnd.nextBytes(it) }
        val ipk = b64(sender.edPub)
        val spk = b64(sender.xPub)
        val epkB64 = b64(epk)
        val nonceB64 = b64(nonce)
        val ad = "msg|1|$conversationId|$mid|$ts|$spk|$ipk|$epkB64|$nonceB64"
        val padded = pad(plaintext.toByteArray(StandardCharsets.UTF_8))
        val ct = gcmSivEncrypt(key, nonce, ad.toByteArray(StandardCharsets.UTF_8), padded)
        Arrays.fill(key, 0)
        Arrays.fill(padded, 0)
        ephemeral.encoded?.let { Arrays.fill(it, 0) }
        val ctB64 = b64(ct)
        val canonical = "$ad|$ctB64"
        val sig = sign(sender.edPriv, canonical)
        // 发送方自己也要能重新打开这段聊天：收件人密文只有收件人私钥能解，
        // 所以额外留一份用发送方自身公钥封装的副本，只用于本机显示，服务端仍不可读。
        val selfCopy = runCatching {
            encryptSelfCopy(sender, conversationId, mid, ts, plaintext)
        }.getOrNull()
        return PREFIX + JSONObject()
            .put("t", "msg")
            .put("v", 1)
            .put("alg", ALG)
            .put("cid", conversationId)
            .put("mid", mid)
            .put("ts", ts)
            .put("ipk", ipk)
            .put("spk", spk)
            .put("epk", epkB64)
            .put("n", nonceB64)
            .put("ct", ctB64)
            .put("sig", b64(sig))
            .apply { if (selfCopy != null) put("self", selfCopy) }
            .toString()
    }

    private fun encryptSelfCopy(
        sender: Identity,
        conversationId: Int,
        mid: String,
        ts: Long,
        plaintext: String
    ): JSONObject {
        val ephemeral = X25519PrivateKeyParameters(rnd)
        val epk = ephemeral.generatePublicKey().encoded
        val staticShared = agree(sender.xPriv, sender.xPub)
        val ephShared = agree(ephemeral.encoded, sender.xPub)
        val key = hkdf(
            staticShared + ephShared,
            "MYE2EE|v1|$conversationId".toByteArray(StandardCharsets.UTF_8),
            "MYE2EE|v1|self|$mid|$conversationId".toByteArray(StandardCharsets.UTF_8),
            32
        )
        val nonce = ByteArray(12).also { rnd.nextBytes(it) }
        val ad = "self|1|$conversationId|$mid|$ts|${b64(sender.xPub)}|${b64(sender.edPub)}|" +
            "${b64(epk)}|${b64(nonce)}"
        val padded = pad(plaintext.toByteArray(StandardCharsets.UTF_8))
        val ct = gcmSivEncrypt(key, nonce, ad.toByteArray(StandardCharsets.UTF_8), padded)
        Arrays.fill(key, 0)
        Arrays.fill(padded, 0)
        ephemeral.encoded?.let { Arrays.fill(it, 0) }
        val ctB64 = b64(ct)
        return JSONObject()
            .put("epk", b64(epk))
            .put("n", b64(nonce))
            .put("ct", ctB64)
            .put("sig", b64(sign(sender.edPriv, "$ad|$ctB64")))
    }

    private fun decryptSelfCopy(
        recipient: Identity,
        conversationId: Int,
        obj: JSONObject
    ): String? = runCatching {
        val self = obj.optJSONObject("self") ?: return@runCatching null
        val epk = b64d(self.getString("epk"))
        val n = self.getString("n")
        val ct = self.getString("ct")
        val mid = obj.getString("mid")
        val ts = obj.getLong("ts")
        val ad = "self|1|$conversationId|$mid|$ts|${obj.getString("spk")}|${obj.getString("ipk")}|" +
            "$epk|$n"
        val ipk = b64d(obj.getString("ipk"))
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(ipk))
        val data = "$ad|$ct".toByteArray(StandardCharsets.UTF_8)
        verifier.update(data, 0, data.size)
        if (!verifier.verifySignature(b64d(self.getString("sig")))) return@runCatching null
        val staticShared = agree(recipient.xPriv, recipient.xPub)
        val ephShared = agree(recipient.xPriv, epk)
        val key = hkdf(
            staticShared + ephShared,
            "MYE2EE|v1|$conversationId".toByteArray(StandardCharsets.UTF_8),
            "MYE2EE|v1|self|$mid|$conversationId".toByteArray(StandardCharsets.UTF_8),
            32
        )
        val padded = gcmSivDecrypt(key, b64d(n), ad.toByteArray(StandardCharsets.UTF_8), b64d(ct))
        Arrays.fill(key, 0)
        padded?.let {
            try {
                unpad(it).toString(StandardCharsets.UTF_8)
            } finally {
                Arrays.fill(it, 0)
            }
        }
    }.getOrNull()

    private fun decryptEnvelope(recipient: Identity, conversationId: Int, envelope: String): String? {
        val obj = JSONObject(strip(envelope))
        if (obj.optString("t") != "msg") return null
        if (!verifyMessage(obj)) return null
        return decryptMessage(recipient, conversationId, obj)
    }

    private fun decryptMessage(
        recipient: Identity,
        conversationId: Int,
        obj: JSONObject
    ): String? {
        val cid = obj.optInt("cid", -1)
        if (cid != conversationId) return null
        val epk = b64d(obj.getString("epk"))
        val spk = b64d(obj.getString("spk"))
        val staticShared = agree(recipient.xPriv, spk)
        val ephShared = agree(recipient.xPriv, epk)
        val mid = obj.getString("mid")
        val key = hkdf(
            staticShared + ephShared,
            "MYE2EE|v1|$conversationId".toByteArray(StandardCharsets.UTF_8),
            "MYE2EE|v1|msg|$mid|$conversationId".toByteArray(StandardCharsets.UTF_8),
            32
        )
        val nonce = b64d(obj.getString("n"))
        val ct = b64d(obj.getString("ct"))
        val ad = "msg|1|$conversationId|$mid|${obj.getLong("ts")}|${obj.getString("spk")}|" +
            "${obj.getString("ipk")}|${obj.getString("epk")}|${obj.getString("n")}"
        val padded = gcmSivDecrypt(key, nonce, ad.toByteArray(StandardCharsets.UTF_8), ct)
        Arrays.fill(key, 0)
        if (padded == null) return decryptSelfCopy(recipient, conversationId, obj)
        return try {
            unpad(padded).toString(StandardCharsets.UTF_8)
        } finally {
            Arrays.fill(padded, 0)
        }
    }

    private fun verifyMessage(obj: JSONObject): Boolean = runCatching {
        val ipk = b64d(obj.getString("ipk"))
        val mid = obj.getString("mid")
        val ts = obj.getLong("ts")
        val ad = "msg|1|${obj.getInt("cid")}|$mid|$ts|${obj.getString("spk")}|${obj.getString("ipk")}|" +
            "${obj.getString("epk")}|${obj.getString("n")}"
        val canonical = "$ad|${obj.getString("ct")}"
        val sig = b64d(obj.getString("sig"))
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(ipk))
        val data = canonical.toByteArray(StandardCharsets.UTF_8)
        verifier.update(data, 0, data.size)
        verifier.verifySignature(sig)
    }.getOrDefault(false)

    private fun sign(privateKey: ByteArray, canonical: String): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privateKey))
        val data = canonical.toByteArray(StandardCharsets.UTF_8)
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    private fun agree(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val out = ByteArray(32)
        X25519PrivateKeyParameters(privateKey).generateSecret(X25519PublicKeyParameters(publicKey), out, 0)
        return out
    }

    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = hmacSha256(salt, ikm)
        val out = ByteArray(length)
        var t = ByteArray(0)
        var done = 0
        var counter = 1
        while (done < length) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val n = minOf(t.size, length - done)
            System.arraycopy(t, 0, out, done, n)
            done += n
            counter++
        }
        Arrays.fill(prk, 0)
        Arrays.fill(t, 0)
        return out
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun gcmSivEncrypt(key: ByteArray, nonce: ByteArray, ad: ByteArray, plain: ByteArray): ByteArray {
        val cipher = GCMSIVBlockCipher()
        cipher.init(
            true,
            AEADParameters(KeyParameter(key), 128, nonce, ad)
        )
        val out = ByteArray(cipher.getOutputSize(plain.size))
        var len = cipher.processBytes(plain, 0, plain.size, out, 0)
        len += cipher.doFinal(out, len)
        return out.copyOf(len)
    }

    private fun gcmSivDecrypt(
        key: ByteArray,
        nonce: ByteArray,
        ad: ByteArray,
        cipherText: ByteArray
    ): ByteArray? = runCatching {
        val cipher = GCMSIVBlockCipher()
        cipher.init(
            false,
            AEADParameters(KeyParameter(key), 128, nonce, ad)
        )
        val out = ByteArray(cipher.getOutputSize(cipherText.size))
        var len = cipher.processBytes(cipherText, 0, cipherText.size, out, 0)
        len += cipher.doFinal(out, len)
        out.copyOf(len)
    }.getOrNull()

    /** 填充到 2KB / 8KB / 32KB 档位；前 4 字节存原文长度，解密后裁剪。 */
    private fun pad(raw: ByteArray): ByteArray {
        val bucket = when {
            raw.size + 4 <= 2048 -> 2048
            raw.size + 4 <= 8192 -> 8192
            raw.size + 4 <= 32768 -> 32768
            else -> ((raw.size + 4 + 32767) / 32768) * 32768
        }
        val out = ByteArray(bucket)
        out[0] = (raw.size ushr 24).toByte()
        out[1] = (raw.size ushr 16).toByte()
        out[2] = (raw.size ushr 8).toByte()
        out[3] = raw.size.toByte()
        System.arraycopy(raw, 0, out, 4, raw.size)
        return out
    }

    private fun unpad(padded: ByteArray): ByteArray {
        if (padded.size < 4) throw IllegalArgumentException("bad padding")
        val len = ((padded[0].toInt() and 0xff) shl 24) or
            ((padded[1].toInt() and 0xff) shl 16) or
            ((padded[2].toInt() and 0xff) shl 8) or
            (padded[3].toInt() and 0xff)
        if (len < 0 || len > padded.size - 4) throw IllegalArgumentException("bad padding")
        return padded.copyOfRange(4, 4 + len)
    }

    // ---------------- 安全码 / 编码 ----------------

    private fun fingerprint(publicKeys: List<ByteArray>): String {
        val digest = Blake3Digest(256)
        publicKeys.map { b64(it) }.sorted().forEach {
            val bytes = it.toByteArray(StandardCharsets.UTF_8)
            digest.update(bytes, 0, bytes.size)
        }
        val out = ByteArray(32)
        digest.doFinal(out, 0)
        var value = 0L
        for (i in 0 until 8) value = (value shl 8) or (out[i].toLong() and 0xff)
        value = value ushr 4
        val digits = String.format(Locale.US, "%018d", value)
        return digits.chunked(6).joinToString("-")
    }

    private fun strip(envelope: String): String =
        if (envelope.startsWith(PREFIX)) envelope.substring(PREFIX.length) else envelope

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun b64d(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    // ---------------- Android Keystore 包裹 ----------------

    private fun keyStoreKey(context: Context): SecretKey {
        val alias = KEYSTORE_ALIAS_PREFIX + UserScope.uid
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        runCatching { ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry }
            .getOrNull()?.secretKey?.let { return it }

        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    private fun seal(context: Context, data: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyStoreKey(context))
        val iv = cipher.iv
        val ct = cipher.doFinal(data)
        return b64(iv) + "." + b64(ct)
    }

    private fun unseal(context: Context, sealed: String): ByteArray {
        val parts = sealed.split('.', limit = 2)
        require(parts.size == 2)
        val iv = b64d(parts[0])
        val ct = b64d(parts[1])
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keyStoreKey(context), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }

    // ---------------- 诊断 ----------------

    /** 给工程排查用：只写状态，不写任何密钥或明文。 */
    fun debugState(context: Context): String =
        "enabled=${isEnabled(context)} identity=${hasIdentity(context)}"
}
