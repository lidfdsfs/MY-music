package com.example.my.util

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** 不重编码的容器重封装：把源文件里的轨道原样搬进新的 MP4/MOV */
object MediaRemux {

    /** 所有轨道（视频 + AAC 音频）原样搬 */
    fun remux(src: File, dst: File): Boolean = runCatching {
        val extractor = MediaExtractor().apply { setDataSource(src.absolutePath) }
        val muxer = MediaMuxer(dst.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val trackMap = mutableMapOf<Int, Int>()
            for (i in 0 until extractor.trackCount) {
                val format: MediaFormat = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.equals("audio/mp4a-latm", true)) {
                    trackMap[i] = muxer.addTrack(format)
                    extractor.selectTrack(i)
                }
            }
            if (trackMap.isEmpty()) return false
            muxer.start()
            val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                info.offset = 0
                info.size = extractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                val muxTrack = trackMap[extractor.sampleTrackIndex] ?: run { extractor.advance(); continue }
                info.presentationTimeUs = extractor.sampleTime
                info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else 0
                muxer.writeSampleData(muxTrack, buffer, info)
                extractor.advance()
            }
            true
        } finally {
            runCatching { muxer.stop() }
            runCatching { muxer.release() }
            runCatching { extractor.release() }
        }
    }.getOrDefault(false)

    /** 只搬视频轨 */
    fun copyVideo(src: File, dst: File): Boolean = copyOnly(src, dst, video = true)

    private fun copyOnly(src: File, dst: File, video: Boolean): Boolean = runCatching {
        val extractor = MediaExtractor().apply { setDataSource(src.absolutePath) }
        val muxer = MediaMuxer(dst.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            var srcTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                val match = if (video) mime.startsWith("video/") else mime.startsWith("audio/")
                if (match) { srcTrack = i; format = f; break }
            }
            val f = format ?: return false
            extractor.selectTrack(srcTrack)
            val outTrack = muxer.addTrack(f)
            muxer.start()
            val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                info.offset = 0
                info.size = extractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                info.presentationTimeUs = extractor.sampleTime
                info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else 0
                muxer.writeSampleData(outTrack, buffer, info)
                extractor.advance()
            }
            true
        } finally {
            runCatching { muxer.stop() }
            runCatching { muxer.release() }
            runCatching { extractor.release() }
        }
    }.getOrDefault(false)

    /** 把「视频源」的视频轨与「音频源」的音频轨合成一个文件（两者都是已编码的成品） */
    fun mux(videoSource: File, audioSource: File?, dst: File): Boolean = runCatching {
        val muxer = MediaMuxer(dst.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val extractors = mutableListOf<MediaExtractor>()
        try {
            val videoExtractor = MediaExtractor().apply { setDataSource(videoSource.absolutePath) }
            extractors.add(videoExtractor)
            var videoFormat: MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val f = videoExtractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/")) { videoFormat = f; break }
            }
            val vf = videoFormat ?: return false
            val videoTrack = muxer.addTrack(vf)

            var audioExtractor: MediaExtractor? = null
            var audioFormat: MediaFormat? = null
            if (audioSource != null && audioSource.exists()) {
                audioExtractor = MediaExtractor().apply { setDataSource(audioSource.absolutePath) }
                extractors.add(audioExtractor)
                for (i in 0 until audioExtractor.trackCount) {
                    val f = audioExtractor.getTrackFormat(i)
                    if (f.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) { audioFormat = f; break }
                }
            }
            val audioTrack = audioFormat?.let { muxer.addTrack(it) } ?: -1

            muxer.start()
            copySamples(videoExtractor, muxer, videoTrack)
            if (audioExtractor != null && audioTrack >= 0) copySamples(audioExtractor, muxer, audioTrack)
            true
        } finally {
            runCatching { muxer.stop() }
            runCatching { muxer.release() }
            extractors.forEach { runCatching { it.release() } }
        }
    }.getOrDefault(false)

    private fun copySamples(extractor: MediaExtractor, muxer: MediaMuxer, track: Int) {
        val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()
        while (true) {
            info.offset = 0
            info.size = extractor.readSampleData(buffer, 0)
            if (info.size < 0) break
            info.presentationTimeUs = extractor.sampleTime
            info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                MediaCodec.BUFFER_FLAG_KEY_FRAME
            } else 0
            muxer.writeSampleData(track, buffer, info)
            extractor.advance()
        }
    }
}
