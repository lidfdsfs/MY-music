package com.example.my.data.vault

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Properties
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 隐私空间：把照片/视频加密后搬进应用私有目录，相册里就再也看不到原件。
 *
 * 安全模型（不依赖任何第三方库，全部走系统 JCE）：
 *  - 密码 → PBKDF2(12 万次) → 256 位 AES 密钥，**密钥只在内存里**，不落盘；
 *  - 每个文件单独 AES-GCM 加密（随机 IV + 认证标签），改一个字节解密就失败；
 *  - 磁盘上只留 salt 与校验值（也是 PBKDF2 派生后再 SHA-256），拿到 meta 也推不出密码；
 *  - 目录本身在 filesDir 下（App 私有，未 root 的机器别的应用读不到），再加一层加密。
 *
 * 「连续错 3 次 → 设备凭证验证 → 失败销毁」的计数存在 meta 里，重启也保留。
 */
object PrivacyVault {

    /** 密码连续错误达到这个次数就要求设备凭证验证 */
    const val MAX_FAILS = 3

    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private val gson = Gson()

    /** 解锁后驻留在内存里的会话：密钥 + 条目索引 */
    private class Session(val key: SecretKey, var items: List<VaultItem>)

    @Volatile
    private var session: Session? = null

    // ---------------- 路径 ----------------

    private fun root(ctx: Context) = com.example.my.data.UserScope.dir(ctx, "privacy_vault")
    private fun mediaDir(ctx: Context) = File(root(ctx), "m")
    private fun previewDir(ctx: Context) = File(ctx.cacheDir, "vault_preview")
    private fun thumbDir(ctx: Context) = File(ctx.cacheDir, "vault_thumb")
    private fun metaFile(ctx: Context) = File(root(ctx), "vault.meta")
    private fun indexFile(ctx: Context) = File(root(ctx), "index.enc")

    // ---------------- 会话状态 ----------------

    fun isConfigured(ctx: Context): Boolean = metaFile(ctx).isFile

    fun hasSession(): Boolean = session != null

    fun items(): List<VaultItem> = session?.items ?: emptyList()

    fun failureCount(ctx: Context): Int =
        runCatching { loadProps(ctx).getProperty("fails")?.toIntOrNull() ?: 0 }.getOrDefault(0)

    fun lock() {
        session = null
    }

    // ---------------- 首次设置 / 解锁 ----------------

    /** 首次进入隐私空间：写入 salt 与校验值，并用空索引初始化 */
    suspend fun setup(ctx: Context, password: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
            val key = deriveKey(password, salt)
            val props = Properties()
            props.setProperty("v", "1")
            props.setProperty("salt", salt.toHex())
            props.setProperty("check", verifier(key).toHex())
            props.setProperty("fails", "0")
            root(ctx).mkdirs()
            saveProps(ctx, props)
            writeIndex(ctx, key, emptyList())
            session = Session(key, emptyList())
            true
        }.getOrDefault(false)
    }

    /**
     * 校验密码。
     * 成功：清零失败计数、加载索引、建立会话；
     * 失败：计数 +1 并落盘（下次启动继续累计），由调用方决定是否触发设备验证。
     */
    suspend fun unlock(ctx: Context, password: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val props = loadProps(ctx)
            val salt = props.getProperty("salt")?.hexToBytes() ?: return@runCatching false
            val expect = props.getProperty("check")?.hexToBytes() ?: return@runCatching false
            val key = deriveKey(password, salt)
            if (!MessageDigest.isEqual(verifier(key), expect)) {
                val fails = (props.getProperty("fails")?.toIntOrNull() ?: 0) + 1
                props.setProperty("fails", fails.toString())
                saveProps(ctx, props)
                return@runCatching false
            }
            props.setProperty("fails", "0")
            saveProps(ctx, props)
            session = Session(key, readIndex(ctx, key))
            true
        }.getOrDefault(false)
    }

    /** 设备凭证验证通过：把错误计数清零，密码本身仍然需要用户自己回忆 */
    suspend fun resetFails(ctx: Context) = withContext(Dispatchers.IO) {
        runCatching {
            val props = loadProps(ctx)
            props.setProperty("fails", "0")
            saveProps(ctx, props)
        }
    }

    /** 销毁：删掉全部密文、索引、密码与缩略图缓存（不可恢复） */
    suspend fun wipe(ctx: Context) = withContext(Dispatchers.IO) {
        session = null
        runCatching { root(ctx).deleteRecursively() }
        runCatching { previewDir(ctx).deleteRecursively() }
        runCatching { thumbDir(ctx).deleteRecursively() }
        Unit
    }

    // ---------------- 导入（相册 → 隐私空间） ----------------

    /**
     * 逐个加密导入。返回成功条数。
     * 边读边加密写盘，大视频也不会一次性读进内存。
     */
    suspend fun import(
        ctx: Context,
        uris: List<Uri>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        val s = session ?: return@withContext 0
        val resolver = ctx.contentResolver
        val added = ArrayList<VaultItem>()
        uris.forEachIndexed { index, uri ->
            runCatching {
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                val kind = if (mime.startsWith("video/")) VaultKind.VIDEO else VaultKind.IMAGE
                val id = UUID.randomUUID().toString().replace("-", "")
                val dest = File(mediaDir(ctx), "m_$id.bin")
                dest.parentFile?.mkdirs()
                val size = resolver.openInputStream(uri)?.use { input ->
                    encryptTo(s.key, input, dest)
                } ?: -1L
                if (size <= 0) {
                    dest.delete()
                    return@runCatching
                }
                added += VaultItem(
                    id = id,
                    name = displayName(ctx, uri),
                    mime = mime,
                    kind = kind,
                    size = size,
                    addedAt = System.currentTimeMillis()
                )
            }
            onProgress(index + 1, uris.size)
        }
        if (added.isNotEmpty()) {
            val merged = added + s.items
            writeIndex(ctx, s.key, merged)
            s.items = merged
        }
        added.size
    }

    // ---------------- 移出（隐私空间 → 相册） ----------------

    /**
     * 解密并写回系统相册（Pictures/Movies 下的「MY足迹隐私空间」），写完删掉密文。
     * 返回成功条数；失败的条目原样保留在隐私空间里，不会丢数据。
     */
    suspend fun exportToGallery(ctx: Context, ids: Set<String>): Int = withContext(Dispatchers.IO) {
        val s = session ?: return@withContext 0
        val done = ArrayList<String>()
        s.items.filter { it.id in ids }.forEach { item ->
            runCatching {
                if (writeToGallery(ctx, s.key, item)) done += item.id
            }
        }
        if (done.isNotEmpty()) {
            done.forEach { id -> File(mediaDir(ctx), "m_$id.bin").delete() }
            val left = s.items.filterNot { it.id in done }
            writeIndex(ctx, s.key, left)
            s.items = left
            cleanupCaches(ctx, done)
        }
        done.size
    }

    /** 彻底删除（不导出），用于「这些我不要了」 */
    suspend fun deleteItems(ctx: Context, ids: Set<String>): Int = withContext(Dispatchers.IO) {
        val s = session ?: return@withContext 0
        val hit = s.items.filter { it.id in ids }
        if (hit.isEmpty()) return@withContext 0
        hit.forEach { runCatching { File(mediaDir(ctx), "m_${it.id}.bin").delete() } }
        val left = s.items.filterNot { it.id in ids }
        writeIndex(ctx, s.key, left)
        s.items = left
        cleanupCaches(ctx, hit.map { it.id })
        hit.size
    }

    /**
     * 导入后删除相册原图。
     *
     * Android 11+ 必须由系统弹一次授权框（MediaStore.createDeleteRequest），
     * 所以真正的删除动作在 UI 层发起；这里只负责老系统（10 及以下）的尽力删除，
     * 删不掉的（没有写权限 / 需要逐条授权）返回失败条数，交给 UI 提示用户手动删。
     */
    suspend fun deleteOriginals(ctx: Context, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var failed = 0
        uris.forEach { uri ->
            runCatching { ctx.contentResolver.delete(uri, null, null) }
                .onFailure { failed++ }
        }
        failed
    }

    // ---------------- 预览（解密到缓存） ----------------

    /** 查看大图/播放视频：把密文解密到缓存目录，返回明文临时文件 */
    suspend fun previewFile(ctx: Context, item: VaultItem): File? = withContext(Dispatchers.IO) {
        val s = session ?: return@withContext null
        runCatching {
            val out = File(previewDir(ctx), "${item.id}.bin")
            if (!out.isFile || out.length() <= 0) {
                out.parentFile?.mkdirs()
                val src = File(mediaDir(ctx), "m_${item.id}.bin")
                if (!src.isFile) return@runCatching null
                FileOutputStream(out).use { fos -> decryptTo(s.key, src, fos) }
            }
            out
        }.getOrNull()
    }

    /** 图片缩略图：解密一次 → 缩小成 400px 的 JPEG 放缓存，列表滚动不重复解密 */
    suspend fun thumbFile(ctx: Context, item: VaultItem): File? = withContext(Dispatchers.IO) {
        if (item.kind != VaultKind.IMAGE) return@withContext null
        val s = session ?: return@withContext null
        val thumb = File(thumbDir(ctx), "${item.id}.jpg")
        if (thumb.isFile && thumb.length() > 0) return@withContext thumb
        runCatching {
            val src = File(mediaDir(ctx), "m_${item.id}.bin")
            if (!src.isFile) return@runCatching null
            thumb.parentFile?.mkdirs()
            val tmp = File(previewDir(ctx), "${item.id}.src")
            tmp.parentFile?.mkdirs()
            FileOutputStream(tmp).use { decryptTo(s.key, src, it) }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmp.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 400 && bounds.outHeight / (sample * 2) >= 400) sample *= 2
            val bmp = BitmapFactory.decodeFile(
                tmp.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
            tmp.delete()
            if (bmp == null) return@runCatching null
            FileOutputStream(thumb).use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            bmp.recycle()
            thumb
        }.getOrNull()
    }

    /** 查看大图用：解密后按最长边 2400 采样，避免超大图 OOM */
    suspend fun fullImage(ctx: Context, item: VaultItem): Bitmap? = withContext(Dispatchers.IO) {
        val f = previewFile(ctx, item) ?: return@withContext null
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            var sample = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / (sample * 2) >= 2400) sample *= 2
            BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    /** 离开页面时清掉解密到缓存的明文 */
    fun clearPreview(ctx: Context) {
        runCatching { previewDir(ctx).deleteRecursively() }
        runCatching { thumbDir(ctx).deleteRecursively() }
    }

    // ---------------- 加密原语 ----------------

    private fun deriveKey(password: String, salt: ByteArray): SecretKey {
        // Android 8.0 起才有 PBKDF2WithHmacSHA256；更老的机器退回 SHA1（迭代次数相同）
        val algorithm =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) "PBKDF2WithHmacSHA256"
            else "PBKDF2WithHmacSHA1"
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        val raw = SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    /** 校验值：由派生密钥再散一次，磁盘上拿不到密钥本身 */
    private fun verifier(key: SecretKey): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(key.encoded)
        md.update("my-vault-check-v1".toByteArray(Charsets.UTF_8))
        return md.digest()
    }

    /** 流式加密：文件头写 12 字节 IV，后面是 GCM 密文（尾部自带认证标签） */
    private fun encryptTo(key: SecretKey, input: InputStream, dest: File): Long {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        var size = 0L
        FileOutputStream(dest).use { fos ->
            fos.write(iv)
            CipherOutputStream(fos, cipher).use { cos ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    cos.write(buf, 0, n)
                    size += n
                }
            }
        }
        return size
    }

    private fun decryptTo(key: SecretKey, src: File, out: OutputStream) {
        src.inputStream().use { input ->
            val iv = ByteArray(IV_BYTES)
            var read = 0
            while (read < IV_BYTES) {
                val n = input.read(iv, read, IV_BYTES - read)
                if (n <= 0) throw IllegalStateException("密文损坏")
                read += n
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                cipher.update(buf, 0, n)?.let { out.write(it) }
            }
            cipher.doFinal()?.let { out.write(it) }
        }
    }

    private fun encryptBytes(key: SecretKey, plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return iv + cipher.doFinal(plain)
    }

    private fun decryptBytes(key: SecretKey, blob: ByteArray): ByteArray {
        if (blob.size <= IV_BYTES) throw IllegalStateException("密文损坏")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(TAG_BITS, blob.copyOfRange(0, IV_BYTES))
        )
        return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }

    // ---------------- 索引 / meta ----------------

    private fun readIndex(ctx: Context, key: SecretKey): List<VaultItem> {
        val f = indexFile(ctx)
        if (!f.isFile) return emptyList()
        return runCatching {
            val plain = decryptBytes(key, f.readBytes()).toString(Charsets.UTF_8)
            val type = object : TypeToken<List<VaultItem>>() {}.type
            gson.fromJson<List<VaultItem>>(plain, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun writeIndex(ctx: Context, key: SecretKey, items: List<VaultItem>) {
        root(ctx).mkdirs()
        val blob = encryptBytes(key, gson.toJson(items).toByteArray(Charsets.UTF_8))
        val tmp = File(root(ctx), "index.tmp")
        tmp.writeBytes(blob)
        // 先写临时文件再改名，避免写一半断电把索引写坏
        val target = indexFile(ctx)
        if (!tmp.renameTo(target)) {
            target.writeBytes(blob)
            tmp.delete()
        }
    }

    private fun loadProps(ctx: Context): Properties {
        val p = Properties()
        metaFile(ctx).takeIf { it.isFile }?.inputStream()?.use { p.load(it) }
        return p
    }

    private fun saveProps(ctx: Context, props: Properties) {
        root(ctx).mkdirs()
        val f = metaFile(ctx)
        val tmp = File(root(ctx), "vault.meta.tmp")
        tmp.outputStream().use { props.store(it, "MY足迹 隐私空间") }
        if (!tmp.renameTo(f)) {
            f.outputStream().use { props.store(it, "MY足迹 隐私空间") }
            tmp.delete()
        }
    }

    // ---------------- 相册写入 ----------------

    private fun writeToGallery(ctx: Context, key: SecretKey, item: VaultItem): Boolean {
        val resolver = ctx.contentResolver
        val isVideo = item.kind == VaultKind.VIDEO
        val name = item.name.ifBlank { item.id + if (isVideo) ".mp4" else ".jpg" }
        val collection =
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    (if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) +
                        "/MY足迹隐私空间"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(
                        if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                    ),
                    "MY足迹隐私空间"
                )
                dir.mkdirs()
                put(MediaStore.MediaColumns.DATA, File(dir, name).absolutePath)
            }
        }
        val uri = resolver.insert(collection, values) ?: return false
        runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                decryptTo(key, File(mediaDir(ctx), "m_${item.id}.bin"), out)
            } ?: throw IllegalStateException("无法写入相册")
        }.onFailure {
            runCatching { resolver.delete(uri, null, null) }
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null
                )
            }
        }
        return true
    }

    private fun cleanupCaches(ctx: Context, ids: List<String>) {
        ids.forEach { id ->
            runCatching { File(previewDir(ctx), "$id.bin").delete() }
            runCatching { File(previewDir(ctx), "$id.src").delete() }
            runCatching { File(thumbDir(ctx), "$id.jpg").delete() }
        }
    }

    private fun displayName(ctx: Context, uri: Uri): String {
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) return c.getString(idx) ?: ""
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "media"
    }

    // ---------------- 小工具 ----------------

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) sb.append("%02x".format(b))
        return sb.toString()
    }

    private fun String.hexToBytes(): ByteArray {
        val out = ByteArray(length / 2)
        for (i in out.indices) {
            out[i] = ((this[i * 2].digitToInt(16) shl 4) or this[i * 2 + 1].digitToInt(16)).toByte()
        }
        return out
    }
}

/** 隐私空间里的一个文件（索引本身也是加密存的） */
data class VaultItem(
    val id: String = "",
    val name: String = "",
    val mime: String = "",
    val kind: VaultKind = VaultKind.IMAGE,
    val size: Long = 0L,
    val addedAt: Long = 0L
)

enum class VaultKind { IMAGE, VIDEO }
