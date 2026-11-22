package com.example.my.data.music

/**
 * 恢复播放位置的安全边界。
 *
 * - 没有保存位置（0 或负数）就从 0 开始；
 * - 时长未知（网页音频还没 prepared）时保留保存的位置；
 * - 时长已知时最多恢复到结尾前 1 秒，避免一打开就立刻触发播放完成。
 */
internal fun clampResumePosition(savedMs: Long, durationMs: Long): Long {
    if (savedMs <= 0L) return 0L
    if (durationMs <= 0L) return savedMs
    return savedMs.coerceAtMost((durationMs - 1_000L).coerceAtLeast(0L))
}
