package top.ispace.wiseeye.rtsp

import android.util.Base64

/**
 * RTSP DESCRIBE 的 SDP 生成器（视频 H.264 + 音频 AAC）。
 */
object SdpBuilder {

    private val HEX = "0123456789ABCDEF"

    fun build(
        sps: ByteArray?,
        pps: ByteArray?,
        aacConfig: ByteArray?,
        sampleRate: Int,
        channelCount: Int
    ): String {
        return buildString {
            append("v=0\r\n")
            append("o=- 0 0 IN IP4 127.0.0.1\r\n")
            append("s=WiseEye\r\n")
            append("c=IN IP4 0.0.0.0\r\n")
            append("t=0 0\r\n")
            append("a=tool:IPCam\r\n")

            // 视频轨
            val profileLevelId = sps?.let { bytesToHex(it, 1, 3) } ?: "42001F"
            val sprop = buildSprop(sps, pps)
            append("m=video 0 RTP/AVP 96\r\n")
            append("a=rtpmap:96 H264/90000\r\n")
            append("a=fmtp:96 packetization-mode=1; profile-level-id=$profileLevelId")
            if (sprop != null) append("; sprop-parameter-sets=$sprop")
            append("\r\n")
            append("a=control:track1\r\n")

            // 音频轨
            if (aacConfig != null) {
                val configHex = bytesToHex(aacConfig, 0, aacConfig.size)
                append("m=audio 0 RTP/AVP 97\r\n")
                append("a=rtpmap:97 MPEG4-GENERIC/$sampleRate/$channelCount\r\n")
                append("a=fmtp:97 streamtype=5; profile-level-id=1; mode=AAC-hbr; config=$configHex; sizeLength=13; indexLength=3; indexDeltaLength=3\r\n")
                append("a=control:track2\r\n")
            }
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
