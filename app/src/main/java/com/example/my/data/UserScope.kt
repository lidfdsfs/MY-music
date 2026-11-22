package com.example.my.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * 本机账号作用域：登录后所有用户文件与偏好都落到 users/<uid>/ 下，换账号互不可见。
 *
 * 说明：
 * - uid=0 表示登录页/未登录，仍用旧目录，只用于登录页背景与公共缓存；
 * - 首次从旧版单账号目录升级时，[activate] 会把旧目录整体搬进当前账号目录，避免升级后丢数据；
 * - 云端恢复、隐私空间、头像、AI 作品、本地备份都通过这里取路径。
 */
object UserScope {

    @Volatile
    var uid: Int = 0
        private set

    private val migrated = HashSet<Int>()

    /** 设置当前账号；仅切 uid，不做磁盘迁移（迁移需要 Context，见 [activate]）。 */
    fun setUid(value: Int?) {
        val next = value ?: 0
        if (next > 0) uid = next
    }

    /**
     * 登录/冷启动进入某个账号时调用：把旧版根目录里没有路径引用的数据搬进 users/<uid>/。
     *
     * 注意：旧版足迹照片的绝对路径已经写进 Room/DataStore，直接搬目录会让历史照片失效，
     * 所以 footprints 与 avatar 不迁移——旧文件留在原地继续可读，新数据写新目录；
     * 隐私空间、AI 作品、本地备份没有外部路径引用，可以整体搬走。
     */
    fun activate(context: Context, value: Int) {
        if (value <= 0) return
        uid = value
        synchronized(migrated) {
            if (!migrated.add(value)) return
        }
        val app = context.applicationContext
        val targetRoot = File(app.filesDir, "users/$value")
        targetRoot.mkdirs()
        listOf("privacy_vault", "ai_creations", "backups").forEach { name ->
            val legacy = File(app.filesDir, name)
            val target = File(targetRoot, name)
            if (legacy.exists() && !target.exists()) {
                runCatching { legacy.renameTo(target) }
                    .onFailure { runCatching { legacy.copyRecursively(target, overwrite = false) } }
            }
        }
        // 旧版 AI 作品元数据是全局 prefs，首次登录复制到当前账号，避免升级后“消失”
        listOf("ai_creations").forEach { base ->
            val legacy = app.getSharedPreferences(base, Context.MODE_PRIVATE)
            val target = app.getSharedPreferences("${base}_$value", Context.MODE_PRIVATE)
            if (target.all.isEmpty() && legacy.all.isNotEmpty()) {
                target.edit().apply { legacy.all.forEach { (k, v) -> putAllSafely(k, v) } }.apply()
            }
        }
    }

    /** SharedPreferences 的 all 值是 Any，按类型写回；未知类型跳过。 */
    private fun SharedPreferences.Editor.putAllSafely(key: String, value: Any?) {
        when (value) {
            is String -> putString(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is Boolean -> putBoolean(key, value)
            is Set<*> -> {
                @Suppress("UNCHECKED_CAST")
                putStringSet(key, value as Set<String>)
            }
            else -> Unit
        }
    }

    /** 当前账号的私有目录；会自动创建。 */
    fun dir(context: Context, name: String): File {
        val root = if (uid > 0) File(context.filesDir, "users/$uid") else context.filesDir
        return File(root, name).apply { mkdirs() }
    }

    /** 当前账号的偏好文件名；登录页/未登录沿用旧名。 */
    fun prefsName(base: String): String = if (uid > 0) "${base}_$uid" else base

    fun prefs(context: Context, base: String): SharedPreferences =
        context.getSharedPreferences(prefsName(base), Context.MODE_PRIVATE)
}
