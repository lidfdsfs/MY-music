package com.example.my.data

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轻量崩溃日志：把未捕获异常追加写入 filesDir/crash.log（上限 [MAX_LINES] 行，超出丢弃最旧），
 * 并让出给系统默认处理器继续（不吞崩溃，保留系统"已停止"弹窗）。
 * 设置页可查看 / 复制 / 清空，便于排查问题后反馈。
 */
object CrashLog {

    private const val MAX_LINES = 400

    /** 幂等：Application 与 Activity 都会调用，重复安装会导致同一次崩溃记多份 */
    @Volatile private var installed = false

    fun install(ctx: Context) {
        if (installed) return
        installed = true
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                append(app, buildCrashText(thread, throwable))
            } catch (_: Exception) {
                // 日志写入失败不影响崩溃处理
            }
            previous?.uncaughtException(thread, throwable)
                ?: run { Process.killProcess(Process.myPid()) }
        }
    }

    fun logFile(ctx: Context): File = File(ctx.filesDir, "crash.log")

    /** 非致命异常也记一笔：后台恢复/同步失败时能看到原因，而不是只剩“闪退” */
    fun record(ctx: Context, throwable: Throwable) {
        runCatching {
            append(ctx, buildCrashText(Thread.currentThread(), throwable))
        }
    }

    fun readLog(ctx: Context): String =
        runCatching { logFile(ctx).readText().trim().ifBlank { "（暂无日志）" } }
            .getOrDefault("（暂无日志）")

    fun clear(ctx: Context) {
        runCatching { logFile(ctx).delete() }
    }

    private fun buildCrashText(thread: Thread, throwable: Throwable): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        return "──── $ts · ${thread.name} ────\n${Log.getStackTraceString(throwable)}"
    }

    private fun append(ctx: Context, text: String) {
        val f = logFile(ctx)
        val existing = runCatching { f.readText() }.getOrDefault("")
        val lines = (existing + "\n" + text).lines().filter { it.isNotBlank() }
        f.writeText(lines.takeLast(MAX_LINES).joinToString("\n"))
    }
}
