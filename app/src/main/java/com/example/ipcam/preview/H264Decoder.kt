package com.example.ipcam.preview

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

/**
 * H.264 硬解码器，解码后渲染到指定 Surface（本地预览）。
 */
class H264Decoder {

    private var codec: MediaCodec? = null

    fun start(surface: Surface, sps: ByteArray?, pps: ByteArray?) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720)
        if (sps != null && pps != null) {
            format.setByteBuffer("csd-0", ByteBuffer.wrap(sps))
            format.setByteBuffer("csd-1", ByteBuffer.wrap(pps))
        }
        val c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        c.configure(format, surface, null, 0)
        c.start()
        codec = c
    }

    fun feed(data: ByteArray) {
        val c = codec ?: return
        try {
            val inIdx = c.dequeueInputBuffer(10_000)
            if (inIdx >= 0) {
                val buf = c.getInputBuffer(inIdx)
                if (buf != null && data.size <= buf.remaining()) {
                    buf.clear()
                    buf.put(data)
                    c.queueInputBuffer(inIdx, 0, data.size, 0, 0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "decoder input error", e)
        }
        drain(c)
    }

    private fun drain(c: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIdx = c.dequeueOutputBuffer(info, 0)
            when {
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                outIdx >= 0 -> c.releaseOutputBuffer(outIdx, true) // 渲染到 Surface
                else -> Unit
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
    }

    companion object {
        private const val TAG = "H264Decoder"
    }
}
