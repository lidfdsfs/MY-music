package com.example.my.util

import android.media.MediaMetadataRetriever
import java.io.File

/**
 * 读本地视频的真实像素尺寸。
 *
 * 用途：播放前先知道画面比例，TextureView 第一帧就按正确比例 fitCenter，
 * 不再出现「视频已经被拉成 16:9 / 裁成一张脸」的那一下；容器也能按视频自身比例排。
 */
object VideoMeta {

    /** 返回 (宽, 高)，读不到时返回 null（调用方退回 16:9） */
    fun size(path: String?): Pair<Int, Int>? {
        if (path.isNullOrBlank()) return null
        // 微足迹的帖子可能是别的用户发在服务端的视频。MediaMetadataRetriever 对 http(s)
        // 会同步拉流并可能长时间阻塞，且没有超时参数：远端一律不探测，交给播放器首帧
        // 自己上报 realAspect，避免一次慢请求拖住探测线程。
        if (path.startsWith("http://") || path.startsWith("https://")) return null
        // 本地路径仍要确认文件还在（缓存被系统清理时直接返回 null，别让播放器空转）
        val file = File(path)
        if (!file.isFile || file.length() <= 0L) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            // 旋转 90/270 的素材，显示尺寸是反的
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            when {
                w <= 0 || h <= 0 -> null
                rotation == 90 || rotation == 270 -> h to w
                else -> w to h
            }
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** 宽高比；读不到时退回 16:9，保证容器总有个可用比例 */
    fun aspect(path: String?): Float {
        val (w, h) = size(path) ?: return 16f / 9f
        if (w <= 0 || h <= 0) return 16f / 9f
        return w.toFloat() / h.toFloat()
    }
}
