package com.example.my.data.music

/**
 * 播放队列的循环边界 —— 唯一真源。
 *
 * 用户要的三条行为全在这里定义，所有切歌入口（播放页、通知、锁屏/耳机
 * MediaSession、桌面小组件、AI 能力）最终都走 `MusicPlayer.next()/prev()`，
 * 因此只在这一处保证首尾回绕即可，别再各写一套 `% size`。
 *
 * - 最后一首播放结束 → 回到第一首继续播（`MusicPlayer.onCompleted`）
 * - 最后一首点「下一曲」→ 第一首（`nextTrackIndex`）
 * - 第一首点「上一曲」→ 最后一首（`previousTrackIndex`）
 */
internal fun nextTrackIndex(currentIndex: Int, size: Int): Int {
    if (size <= 0) return -1
    // currentIndex = size - 1（最后一首）→ 0；= -1（当前歌不在队列里）→ 0。
    // coerceAtLeast 兜住任何异常负下标，避免返回 -1 让切歌按钮「点了没反应」。
    return (currentIndex + 1).coerceAtLeast(0) % size
}

/**
 * 当前播放列表里的上一首下标。
 * 上一首按钮不判断播放进度、不回本曲开头，只按列表顺序取前一首；
 * 第一首再按一次循环到末尾。当前歌不在列表里时从第一首开始。
 */
internal fun previousTrackIndex(currentIndex: Int, size: Int): Int {
    if (size <= 0) return -1
    return when {
        currentIndex < 0 -> 0
        currentIndex == 0 -> size - 1
        else -> (currentIndex - 1).coerceIn(0, size - 1)
    }
}

/**
 * 一首歌自然播放结束后，是否继续按顺序队列往下走。
 * LOOP_ONE 由调用方单独重播当前曲，不走队列；ORDER 只在还没到末尾时续播。
 */
internal fun shouldAutoAdvance(mode: PlayMode, currentIndex: Int, size: Int): Boolean =
    when (mode) {
        PlayMode.LOOP_ALL, PlayMode.SHUFFLE -> true
        PlayMode.LOOP_ONE -> false
        PlayMode.ORDER -> size > 0 && currentIndex in 0 until size - 1
    }
