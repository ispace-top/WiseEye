package com.example.ipcam.stream

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.example.ipcam.util.H264Utils
import com.example.ipcam.util.H264Utils.nalType

/**
 * H.264 硬件编码器（MediaCodec，Surface 输入）。
 * 通过 [start] 返回一个输入 Surface，供相机（CameraX Preview）直接写入，实现零拷贝。
 */
class VideoEncoder {

    private var codec: MediaCodec? = null
    private var sink: VideoFrameSink? = null
    private var handlerThread: HandlerThread? = null

    fun start(width: Int, height: Int, fps: Int, bitrate: Int, sink: VideoFrameSink): Surface {
        this.sink = sink
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2) // GOP 2s，保证首屏与断线恢复
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
        }
        val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = c.createInputSurface()

        // 编码回调跑在后台线程，避免 RTP socket 发送触发 NetworkOnMainThreadException
        val ht = HandlerThread("VideoEncoderThread")
        ht.start()
        handlerThread = ht

        c.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                // Surface 输入模式下无需处理输入 buffer
            }

            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                try {
                    val buffer = codec.getOutputBuffer(index)
                    if (buffer != null && info.size > 0) {
                        val data = ByteArray(info.size)
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.get(data)

                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            val (sps, pps) = extractSpsPps(data)
                            if (sps != null && pps != null) sink?.onSpsPps(sps, pps)
                        } else {
                            val key = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                            sink?.onVideoFrame(data, info.presentationTimeUs, key)
                        }
                    }
                    codec.releaseOutputBuffer(index, false)
                } catch (e: Exception) {
                    Log.e(TAG, "onOutputBufferAvailable error", e)
                    codec.releaseOutputBuffer(index, false)
                }
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                Log.e(TAG, "encoder error", e)
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                // ignore
            }
        }, Handler(ht.looper))
        c.start()
        codec = c
        return inputSurface
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
        handlerThread?.quitSafely()
        handlerThread = null
    }

    private fun extractSpsPps(codecConfig: ByteArray): Pair<ByteArray?, ByteArray?> {
        var sps: ByteArray? = null
        var pps: ByteArray? = null
        for (nal in H264Utils.splitAnnexB(codecConfig)) {
            when (nalType(nal)) {
                7 -> sps = nal
                8 -> pps = nal
            }
        }
        return sps to pps
    }

    companion object {
        private const val TAG = "VideoEncoder"
    }
}
