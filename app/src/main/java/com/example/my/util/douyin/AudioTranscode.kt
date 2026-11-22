package com.example.my.util.douyin

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** 音频统一转成 AAC 128kbps（保留源采样率与声道，避免重采样引入的复杂度） */
object AudioTranscode {

    fun toAac(src: File, dst: File, bitrate: Int = 128_000): Boolean = runCatching {
        val extractor = MediaExtractor().apply { setDataSource(src.absolutePath) }
        var track = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) { track = i; format = f; break }
        }
        val af = format ?: return false
        extractor.selectTrack(track)

        val srcMime = af.getString(MediaFormat.KEY_MIME) ?: return false
        val sampleRate = if (af.containsKey(MediaFormat.KEY_SAMPLE_RATE)) af.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 48_000
        val channels = if (af.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) af.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

        val decoder = MediaCodec.createDecoderByType(srcMime)
        decoder.configure(af, null, null, 0)
        decoder.start()

        val encFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
        }
        val encoder = MediaCodec.createEncoderByType("audio/mp4a-latm")
        encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(dst.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        var outTrack = -1
        val decInfo = MediaCodec.BufferInfo()
        val encInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var decoderInputDone = false
        var encoderDone = false
        var eosQueued = false
        var eosPts = 0L
        var lastPts = 0L

        try {
            val deadline = System.currentTimeMillis() + 120_000L
            while (!encoderDone) {
                if (System.currentTimeMillis() > deadline) throw IllegalStateException("音频转码超时")
                if (!decoderInputDone) {
                    val inIdx = decoder.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx)
                        val size = buf?.let { extractor.readSampleData(it, 0) } ?: -1
                        if (size < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            decoderInputDone = true
                        } else {
                            lastPts = extractor.sampleTime
                            decoder.queueInputBuffer(inIdx, 0, size, lastPts, 0)
                            extractor.advance()
                        }
                    }
                }
                var decIdx = decoder.dequeueOutputBuffer(decInfo, 10_000)
                while (decIdx >= 0) {
                    val eos = decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    if (decInfo.size > 0) {
                        val decBuf = decoder.getOutputBuffer(decIdx)
                        var encIn = encoder.dequeueInputBuffer(10_000)
                        if (encIn < 0) encIn = encoder.dequeueInputBuffer(10_000)
                        if (decBuf != null && encIn >= 0) {
                            decBuf.position(decInfo.offset)
                            decBuf.limit(decInfo.offset + decInfo.size)
                            encoder.getInputBuffer(encIn)?.apply {
                                clear()
                                put(decBuf)
                                encoder.queueInputBuffer(encIn, 0, decInfo.size, decInfo.presentationTimeUs, 0)
                            }
                        }
                    }
                    if (eos) {
                        inputDone = true
                        eosPts = decInfo.presentationTimeUs
                    }
                    decoder.releaseOutputBuffer(decIdx, false)
                    if (eos) break
                    decIdx = decoder.dequeueOutputBuffer(decInfo, 0)
                }

                // 结束信号要持续重试，拿不到输入缓冲就下一轮，否则会一直空转
                if (inputDone && !eosQueued) {
                    val encIn = encoder.dequeueInputBuffer(10_000)
                    if (encIn >= 0) {
                        encoder.queueInputBuffer(encIn, 0, 0, eosPts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        eosQueued = true
                    }
                }

                // 抽干编码器
                while (true) {
                    val idx = encoder.dequeueOutputBuffer(encInfo, 10_000)
                    if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!started) {
                            outTrack = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            started = true
                        }
                        continue
                    }
                    if (idx >= 0) {
                        val config = encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        val buf = encoder.getOutputBuffer(idx)
                        if (!config && encInfo.size > 0 && started && buf != null) {
                            buf.position(encInfo.offset)
                            buf.limit(encInfo.offset + encInfo.size)
                            muxer.writeSampleData(outTrack, buf, encInfo)
                        }
                        encoder.releaseOutputBuffer(idx, false)
                        if (encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) encoderDone = true
                    }
                }
            }
            true
        } finally {
            runCatching { decoder.stop(); decoder.release() }
            runCatching { encoder.stop(); encoder.release() }
            runCatching { if (started) muxer.stop() }
            runCatching { muxer.release() }
            runCatching { extractor.release() }
        }
    }.getOrDefault(false)
}
