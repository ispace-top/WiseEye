package top.ispace.wiseeye.stream

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log

/**
 * AAC 硬件编码器（MediaCodec，同步输入 + 同步输出）。
 * 由 [AudioRecorder] 线程调用 [encodePcm] 喂入 PCM，并在同一线程内 drain 输出。
 */
class AudioEncoder {

    private var codec: MediaCodec? = null
    private var sink: AudioFrameSink? = null
    private var sampleRate = 16000
    private var channelCount = 1

    fun start(sampleRate: Int, channelCount: Int, bitrate: Int, sink: AudioFrameSink) {
        this.sink = sink
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        }
        val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        c.start()
        codec = c
    }

    /** 同步喂入一帧 PCM（16bit），并顺带 drain 输出 */
    fun encodePcm(pcm: ByteArray, ptsUs: Long) {
        val c = codec ?: return
        try {
            val inIdx = c.dequeueInputBuffer(10_000)
            if (inIdx >= 0) {
                val buf = c.getInputBuffer(inIdx)
                if (buf != null && pcm.size <= buf.remaining()) {
                    buf.clear()
                    buf.put(pcm)
                    c.queueInputBuffer(inIdx, 0, pcm.size, ptsUs, 0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "audio input error", e)
        }
        drainOutput(c)
    }

    private fun drainOutput(c: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIdx = c.dequeueOutputBuffer(info, 0)
            when {
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                outIdx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                outIdx >= 0 -> {
                    try {
                        val buf = c.getOutputBuffer(outIdx)
                        if (buf != null && info.size > 0) {
                            val data = ByteArray(info.size)
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            buf.get(data)
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                sink?.onAudioConfig(data, sampleRate, channelCount)
                            } else {
                                sink?.onAudioFrame(data, info.presentationTimeUs)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "audio output error", e)
                    } finally {
                        c.releaseOutputBuffer(outIdx, false)
                    }
                }
            }
        }
    }

    fun stop() {
        try {
            codec?.stop()
        } catch (_: Exception) {
        }
        try {
            codec?.release()
        } catch (_: Exception) {
        }
        codec = null
        sink = null
    }

    companion object {
        private const val TAG = "AudioEncoder"
    }
}
