package com.example.ipcam.rtsp

import android.util.Base64

/**
 * RTSP DESCRIBE 的 SDP 生成器（M0：仅视频 H.264）。
 */
object SdpBuilder {

    private val HEX = "0123456789ABCDEF"

    fun buildVideo(sps: ByteArray?, pps: ByteArray?): String {
        val profileLevelId = sps?.let { bytesToHex(it, 1, 3) } ?: "42001F"
        val sprop = buildSprop(sps, pps)
        return buildString {
            append("v=0\r\n")
            append("o=- 0 0 IN IP4 127.0.0.1\r\n")
            append("s=IPCam\r\n")
            append("c=IN IP4 0.0.0.0\r\n")
            append("t=0 0\r\n")
            append("a=tool:IPCam\r\n")
            append("m=video 0 RTP/AVP 96\r\n")
            append("a=rtpmap:96 H264/90000\r\n")
            append("a=fmtp:96 packetization-mode=1; profile-level-id=$profileLevelId")
            if (sprop != null) append("; sprop-parameter-sets=$sprop")
            append("\r\n")
            append("a=control:track1\r\n")
        }
    }

    private fun buildSprop(sps: ByteArray?, pps: ByteArray?): String? {
        if (sps == null || pps == null) return null
        val s = Base64.encodeToString(sps, Base64.NO_WRAP)
        val p = Base64.encodeToString(pps, Base64.NO_WRAP)
        return "$s,$p"
    }

    private fun bytesToHex(bytes: ByteArray, offset: Int, len: Int): String {
        val sb = StringBuilder()
        val end = (offset + len).coerceAtMost(bytes.size)
        for (i in offset until end) {
            val v = bytes[i].toInt() and 0xFF
            sb.append(HEX[v shr 4])
            sb.append(HEX[v and 0xF])
        }
        return sb.toString()
    }
}
