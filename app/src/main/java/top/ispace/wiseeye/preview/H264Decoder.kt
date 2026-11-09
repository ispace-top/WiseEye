package top.ispace.wiseeye.preview

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
    private var keyframeReceived = false

    fun start(surface: Surface, sps: ByteArray?, pps: ByteArray?) {
        Log.i(TAG, "decoder start sps=${sps?.size} pps=${pps?.size}")
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720)
        val c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        c.configure(format, surface, null, 0)
        c.start()
        codec = c
        // 喂入 SPS/PPS（in-band，兼容性更好，不依赖 CSD）
        if (sps != null) feed(withStartCode(sps))
        if (pps != null) feed(withStartCode(pps))
    }

    private fun withStartCode(nal: ByteArray): ByteArray {
        val out = ByteArray(4 + nal.size)
        out[0] = 0; out[1] = 0; out[2] = 0; out[3] = 1
        System.arraycopy(nal, 0, out, 4, nal.size)
        return out
    }

    fun feed(data: ByteArray) {
        // 等待关键帧(IDR)后再喂入，避免 P 帧无参考导致绿屏
        if (!keyframeReceived) {
            if (data.size <= 4 || (data[4].toInt() and 0x1F) != 5) return
            keyframeReceived = true
        }
        val c = codec ?: return
        try {
            val inIdx = c.dequeueInputBuffer(10_000)
            if (inIdx >= 0) {
                val buf = c.getInputBuffer(inIdx)
                if (buf != null && data.size <= buf.remaining()) {
                    buf.clear()
                    buf.put(data)
                    c.queueInputBuffer(inIdx, 0, data.size, System.nanoTime() / 1000, 0)
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
