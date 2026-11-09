package com.example.ipcam.preview

import android.util.Base64
import android.util.Log
import android.view.Surface
import com.example.ipcam.rtsp.DigestAuth
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.Socket

/**
 * 本地预览：连接本地 RTSP 服务端，解码 H.264 并渲染到 [Surface]。
 * 展示的是编码+RTSP 封装后的实际推流内容（与 EasyNVR 收到的一致）。
 */
class LocalPreview(private val surface: Surface) {

    private var socket: Socket? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null
    private var decoder: H264Decoder? = null
    private var readerThread: Thread? = null

    @Volatile
    private var running = false

    var onStateChanged: ((Boolean) -> Unit)? = null

    fun start(url: String, username: String, password: String) {
        stop()
        running = true
        Thread({ run(url, username, password) }, "LocalPreview").start()
    }

    private fun run(url: String, username: String, password: String) {
        var ok = false
        // 重试连接，等待 RTSP 服务端就绪
        for (attempt in 0 until 10) {
            if (!running) return
            try {
                closeSocket()
                if (connectAndSetup(url, username, password)) {
                    ok = true
                    break
                }
            } catch (e: Exception) {
                Log.w(TAG, "preview connect attempt $attempt failed: ${e.message}")
            }
            if (!running) return
            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
                return
            }
        }
        if (!ok) {
            onStateChanged?.invoke(false)
            return
        }
        onStateChanged?.invoke(true)
        Log.i(TAG, "preview connected, decoding")
        try {
            readLoop()
        } catch (e: Exception) {
            Log.e(TAG, "preview read error", e)
        } finally {
            onStateChanged?.invoke(false)
        }
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        input = null
        output = null
    }

    private fun connectAndSetup(url: String, username: String, password: String): Boolean {
        val (host, portStr, path) = parseUrl(url)
        val port = portStr.toInt()
        socket = Socket(host, port).also { it.soTimeout = 5000 }
        input = BufferedInputStream(socket!!.getInputStream())
        output = BufferedOutputStream(socket!!.getOutputStream())

        val realm = "IPCam"
        var nonce = ""
        // OPTIONS 无认证 → 401 + nonce
        val r1 = send("OPTIONS", url, null, null)
        if (r1.startsWith("RTSP/1.0 401")) {
            nonce = extractNonce(r1) ?: return false
        }

        fun auth(method: String, uri: String): String {
            val resp = DigestAuth.computeDigest(username, realm, password, method, uri, nonce)
            return "Digest username=\"$username\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", response=\"$resp\""
        }

        // OPTIONS 认证
        if (!send("OPTIONS", url, auth("OPTIONS", url), null).startsWith("RTSP/1.0 200")) return false
        // DESCRIBE
        val describe = send("DESCRIBE", url, auth("DESCRIBE", url), "Accept: application/sdp")
        if (!describe.startsWith("RTSP/1.0 200")) return false
        val (sps, pps) = parseSprop(describe)
        // SETUP track1
        val track1 = "$url/track1"
        val setup = send("SETUP", track1, auth("SETUP", track1), "Transport: RTP/AVP/TCP;unicast;interleaved=0-1")
        if (!setup.startsWith("RTSP/1.0 200")) return false
        val session = extractSession(setup) ?: return false
        // PLAY
        if (!send("PLAY", url, auth("PLAY", url), "Session: $session").startsWith("RTSP/1.0 200")) return false

        // 配置解码器
        decoder = H264Decoder()
        decoder?.start(surface, sps, pps)
        return true
    }

    private fun readLoop() {
        val depacketizer = RtpDepacketizer()
        while (running) {
            val b = input?.read() ?: break
            if (b == 0x24) { // '$'
                val channel = input?.read() ?: break
                val hi = input?.read() ?: break
                val lo = input?.read() ?: break
                val len = ((hi shl 8) or lo) and 0xFFFF
                if (len <= 0 || len > 65535) break
                val payload = ByteArray(len)
                if (!readFully(payload)) break
                if (channel == 0) { // 视频 RTP
                    val nal = depacketizer.process(payload)
                    if (nal != null) decoder?.feed(nal)
                }
            }
        }
    }

    private fun send(method: String, uri: String, authHeader: String?, extra: String?): String {
        val out = output!!
        val inp = input!!
        val sb = StringBuilder()
        sb.append("$method $uri RTSP/1.0\r\n")
        sb.append("CSeq: 1\r\n")
        if (authHeader != null) sb.append("Authorization: $authHeader\r\n")
        if (extra != null) sb.append("$extra\r\n")
        sb.append("\r\n")
        synchronized(out) {
            out.write(sb.toString().toByteArray())
            out.flush()
        }
        // 读取响应（含 body）
        val header = StringBuilder()
        var last = '0'
        while (true) {
            val c = inp.read()
            if (c == -1) break
            header.append(c.toChar())
            if (c.toChar() == '\n' && header.length >= 2 && header[header.length - 2] == '\r' &&
                header.length >= 4 && header.substring(header.length - 4) == "\r\n\r\n"
            ) break
            last = c.toChar()
        }
        val headerStr = header.toString()
        val cl = headerStr.lineSequence()
            .firstOrNull { it.startsWith("Content-Length", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
        if (cl > 0) {
            val body = ByteArray(cl)
            var read = 0
            while (read < cl) {
                val r = inp.read(body, read, cl - read)
                if (r == -1) break
                read += r
            }
            return headerStr + String(body, Charsets.UTF_8)
        }
        return headerStr
    }

    private fun readFully(buf: ByteArray): Boolean {
        var read = 0
        while (read < buf.size) {
            val r = input?.read(buf, read, buf.size - read) ?: return false
            if (r == -1) return false
            read += r
        }
        return true
    }

    private fun parseUrl(url: String): Triple<String, String, String> {
        val without = url.substringAfter("rtsp://")
        val hostPort = without.substringBefore('/')
        val path = without.substringAfter('/', "")
        val host = hostPort.substringBefore(':')
        val port = hostPort.substringAfter(':', "8554")
        return Triple(host, port, path)
    }

    private fun extractNonce(response: String): String? =
        Regex("nonce=\"([^\"]+)\"").find(response)?.groupValues?.get(1)

    private fun extractSession(response: String): String? =
        response.lineSequence().firstOrNull { it.startsWith("Session", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.substringBefore(';')?.trim()

    private fun parseSprop(describe: String): Pair<ByteArray?, ByteArray?> {
        val m = Regex("sprop-parameter-sets=([^;\r\n]+)").find(describe) ?: return null to null
        val parts = m.groupValues[1].split(',')
        if (parts.size < 2) return null to null
        return try {
            Base64.decode(parts[0].trim(), Base64.NO_WRAP) to Base64.decode(parts[1].trim(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null to null
        }
    }

    fun stop() {
        running = false
        readerThread?.interrupt()
        decoder?.stop()
        decoder = null
        closeSocket()
    }

    companion object {
        private const val TAG = "LocalPreview"
    }

    /** H.264 RTP → Annex-B 解包（RFC 6184） */
    private class RtpDepacketizer {
        private val fragmentBuffer = ByteArrayOutputStream()

        fun process(payload: ByteArray): ByteArray? {
            if (payload.isEmpty()) return null
            val nalType = payload[0].toInt() and 0x1F
            return when {
                nalType in 1..23 -> withStartCode(payload)
                nalType == 28 -> processFuA(payload)
                else -> null
            }
        }

        private fun processFuA(payload: ByteArray): ByteArray? {
            if (payload.size < 2) return null
            val fuIndicator = payload[0].toInt() and 0xFF
            val fuHeader = payload[1].toInt() and 0xFF
            val isStart = fuHeader and 0x80 != 0
            val isEnd = fuHeader and 0x40 != 0
            val data = payload.copyOfRange(2, payload.size)
            if (isStart) {
                fragmentBuffer.reset()
                val header = (fuIndicator and 0xE0) or (fuHeader and 0x1F)
                fragmentBuffer.write(header)
            }
            fragmentBuffer.write(data)
            if (isEnd) {
                val result = withStartCode(fragmentBuffer.toByteArray())
                fragmentBuffer.reset()
                return result
            }
            return null
        }

        private fun withStartCode(nal: ByteArray): ByteArray {
            val out = ByteArray(4 + nal.size)
            out[0] = 0; out[1] = 0; out[2] = 0; out[3] = 1
            System.arraycopy(nal, 0, out, 4, nal.size)
            return out
        }
    }
}
