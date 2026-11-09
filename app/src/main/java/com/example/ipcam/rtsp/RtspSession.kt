package com.example.ipcam.rtsp

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.Socket

/**
 * 单个 RTSP 客户端会话，负责解析请求、响应，并在 PLAY 后通过 TCP interleaved 发送 RTP。
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

    private var videoRtpChannel = 0
    private var videoRtcpChannel = 1

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
            if (b == -1) return null // EOF
            if (b == '\n'.code) {
                val line = sb.toString().trimEnd('\r')
                sb.clear()
                lines.add(line)
                if (line.isEmpty()) break
            } else {
                sb.append(b.toChar())
            }
        }
        // 处理请求体（本场景客户端通常无 body）
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
        val headers = mutableMapOf<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        val cseq = headers["cseq"] ?: "0"

        when (method.uppercase()) {
            "OPTIONS" -> respond("RTSP/1.0 200 OK", cseq, extra = "Public: OPTIONS, DESCRIBE, SETUP, TEARDOWN, PLAY, GET_PARAMETER\r\n")
            "DESCRIBE" -> respondDescribe(cseq)
            "SETUP" -> respondSetup(cseq, headers)
            "PLAY" -> respondPlay(cseq)
            "TEARDOWN" -> respondTeardown(cseq)
            "GET_PARAMETER" -> respond("RTSP/1.0 200 OK", cseq)
            else -> respond("RTSP/1.0 405 Method Not Allowed", cseq)
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

    private fun respondSetup(cseq: String, headers: Map<String, String>) {
        val transport = headers["transport"] ?: ""
        if (transport.contains("TCP", ignoreCase = true)) {
            val m = Regex("interleaved=(\\d+)-(\\d+)").find(transport)
            if (m != null) {
                videoRtpChannel = m.groupValues[1].toInt()
                videoRtcpChannel = m.groupValues[2].toInt()
            }
        }
        val transportResp = "RTP/AVP/TCP;unicast;interleaved=$videoRtpChannel-$videoRtcpChannel"
        respond("RTSP/1.0 200 OK", cseq, extra = "Transport: $transportResp\r\nSession: $sessionId\r\n")
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

    /** 发送一个 RTP 包（TCP interleaved 帧：$ + channel + len + payload） */
    fun sendRtp(packet: ByteArray) {
        if (!playing || closed) return
        try {
            val header = byteArrayOf(
                0x24, // '$'
                videoRtpChannel.toByte(),
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
