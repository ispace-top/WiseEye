package top.ispace.wiseeye.rtsp

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.Socket

/**
 * 单个 RTSP 客户端会话：解析请求、响应，并在 PLAY 后通过 TCP interleaved 发送 RTP（视频/音频双轨）。
 */
class RtspSession(
    private val socket: Socket,
    private val server: RtspServer,
    private val sessionId: String
) {

    private val input = BufferedInputStream(socket.getInputStream())
    private val output = BufferedOutputStream(socket.getOutputStream())

    @Volatile
    private var playing = false

    @Volatile
    private var closed = false

    // 视频轨（track1）默认 interleaved 0-1，音频轨（track2）默认 2-3
    private var videoRtpChannel = 0
    private var videoRtcpChannel = 1
    private var audioRtpChannel = 2
    private var audioRtcpChannel = 3

    val isPlaying: Boolean get() = playing

    fun run() {
        try {
            while (!closed) {
                val lines = readRequest() ?: break
                if (lines.isNotEmpty()) handleRequest(lines)
            }
        } catch (e: IOException) {
            // 客户端断开
        } finally {
            close()
            server.removeSession(this)
        }
    }

    private fun readRequest(): List<String>? {
        val lines = mutableListOf<String>()
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return null
            if (b == '\n'.code) {
                val line = sb.toString().trimEnd('\r')
                sb.clear()
                lines.add(line)
                if (line.isEmpty()) break
            } else {
                sb.append(b.toChar())
            }
        }
        val contentLength = lines.firstOrNull { it.startsWith("Content-Length", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
        if (contentLength > 0) {
            val body = ByteArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val r = input.read(body, read, contentLength - read)
                if (r == -1) break
                read += r
            }
        }
        return lines
    }

    private fun handleRequest(lines: List<String>) {
        if (lines.isEmpty()) return
        val parts = lines[0].split(' ')
        if (parts.size < 3) return
        val method = parts[0]
        val uri = parts[1]
        val headers = mutableMapOf<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        val cseq = headers["cseq"] ?: "0"

        if (!server.isAuthorized(method, uri, headers["authorization"])) {
            respond401(cseq)
            return
        }

        when (method.uppercase()) {
            "OPTIONS" -> respond("RTSP/1.0 200 OK", cseq, extra = "Public: OPTIONS, DESCRIBE, SETUP, TEARDOWN, PLAY, GET_PARAMETER\r\n")
            "DESCRIBE" -> respondDescribe(cseq)
            "SETUP" -> respondSetup(cseq, uri, headers)
            "PLAY" -> respondPlay(cseq)
            "TEARDOWN" -> respondTeardown(cseq)
            "GET_PARAMETER" -> respond("RTSP/1.0 200 OK", cseq)
            else -> respond("RTSP/1.0 405 Method Not Allowed", cseq)
        }
    }

    private fun respond401(cseq: String) {
        val resp = "RTSP/1.0 401 Unauthorized\r\nCSeq: $cseq\r\nWWW-Authenticate: ${server.authChallenge()}\r\n\r\n"
        synchronized(output) {
            output.write(resp.toByteArray())
            output.flush()
        }
    }

    private fun respondDescribe(cseq: String) {
        val body = server.buildSdp().toByteArray()
        val head = "RTSP/1.0 200 OK\r\n" +
            "CSeq: $cseq\r\n" +
            "Content-Type: application/sdp\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "\r\n"
        synchronized(output) {
            output.write(head.toByteArray())
            output.write(body)
            output.flush()
        }
    }

    private fun respondSetup(cseq: String, uri: String, headers: Map<String, String>) {
        val transport = headers["transport"] ?: ""
        val isAudio = uri.contains("track2", ignoreCase = true)
        var rtpChannel = if (isAudio) audioRtpChannel else videoRtpChannel
        var rtcpChannel = if (isAudio) audioRtcpChannel else videoRtcpChannel
        if (transport.contains("TCP", ignoreCase = true)) {
            val m = Regex("interleaved=(\\d+)-(\\d+)").find(transport)
            if (m != null) {
                rtpChannel = m.groupValues[1].toInt()
                rtcpChannel = m.groupValues[2].toInt()
            }
        }
        if (isAudio) {
            audioRtpChannel = rtpChannel
            audioRtcpChannel = rtcpChannel
        } else {
            videoRtpChannel = rtpChannel
            videoRtcpChannel = rtcpChannel
        }
        respond(
            "RTSP/1.0 200 OK", cseq,
            extra = "Transport: RTP/AVP/TCP;unicast;interleaved=$rtpChannel-$rtcpChannel\r\nSession: $sessionId\r\n"
        )
    }

    private fun respondPlay(cseq: String) {
        playing = true
        respond("RTSP/1.0 200 OK", cseq, extra = "Session: $sessionId\r\nRange: npt=0.000-\r\n")
    }

    private fun respondTeardown(cseq: String) {
        playing = false
        respond("RTSP/1.0 200 OK", cseq, extra = "Session: $sessionId\r\n")
    }

    private fun respond(statusLine: String, cseq: String, extra: String = "") {
        val resp = "$statusLine\r\nCSeq: $cseq\r\n$extra\r\n"
        synchronized(output) {
            output.write(resp.toByteArray())
            output.flush()
        }
    }

    fun sendVideoRtp(packet: ByteArray) = sendRtpOnChannel(packet, videoRtpChannel)

    fun sendAudioRtp(packet: ByteArray) = sendRtpOnChannel(packet, audioRtpChannel)

    private fun sendRtpOnChannel(packet: ByteArray, channel: Int) {
        if (!playing || closed) return
        try {
            val header = byteArrayOf(
                0x24, // '$'
                channel.toByte(),
                ((packet.size shr 8) and 0xFF).toByte(),
                (packet.size and 0xFF).toByte()
            )
            synchronized(output) {
                output.write(header)
                output.write(packet)
                output.flush()
            }
        } catch (e: IOException) {
            close()
        }
    }

    fun close() {
        if (closed) return
        closed = true
        playing = false
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }
}
