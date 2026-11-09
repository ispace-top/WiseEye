package com.example.ipcam.rtmp

import android.util.Log
import com.example.ipcam.stream.AudioFrameSink
import com.example.ipcam.stream.VideoFrameSink
import com.example.ipcam.util.H264Utils
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.Socket
import kotlin.random.Random

/**
 * 轻量 RTMP 推流客户端（简单握手 + AMF0 命令 + FLV 封装）。
 * 实现 [VideoFrameSink]/[AudioFrameSink]，接收 H.264/AAC 帧并推流。
 */
class RtmpPusher : VideoFrameSink, AudioFrameSink {

    private var socket: Socket? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null
    private var streamId = 0

    @Volatile
    var connected = false
        private set

    private var sps: ByteArray? = null
    private var pps: ByteArray? = null
    private var aacConfig: ByteArray? = null
    private var sampleRate = 16000
    private var channelCount = 1

    private var baseTimeMs = 0L

    fun connect(url: String): Boolean {
        return try {
            val (host, portStr, app, stream) = parseUrl(url)
            val s = Socket(host, portStr.toInt())
            s.soTimeout = 5000
            socket = s
            input = BufferedInputStream(s.getInputStream())
            output = BufferedOutputStream(s.getOutputStream())

            handshake()
            val ok = connectFlow(app, stream)
            if (ok) {
                connected = true
                baseTimeMs = System.currentTimeMillis()
                // 连接后发送 FLV header
                sendFlvHeader()
                Log.i(TAG, "RTMP connected to $url")
            }
            ok
        } catch (e: Exception) {
            Log.e(TAG, "RTMP connect failed", e)
            close()
            false
        }
    }

    // ---- 握手（简单握手） ----
    private fun handshake() {
        val out = output!!
        val inp = input!!
        out.write(0x03) // C0
        val c1 = ByteArray(1536)
        Random.nextBytes(c1)
        c1[0] = 0; c1[1] = 0; c1[2] = 0; c1[3] = 0
        out.write(c1); out.flush()

        inp.read() // S0
        val s1 = ByteArray(1536)
        readFully(inp, s1)
        val s2 = ByteArray(1536)
        readFully(inp, s2)

        out.write(s1) // C2 = echo S1
        out.flush()
    }

    // ---- connect / createStream / publish ----
    private fun connectFlow(app: String, stream: String): Boolean {
        val tcUrl = "rtmp://${socket!!.inetAddress.hostAddress}:${socket!!.port}/$app"

        // connect
        sendCommand(3, 0, "connect", 1.0,
            amf0Object(
                "app" to app,
                "type" to "nonprivate",
                "tcUrl" to tcUrl,
                "flashVer" to "FMLE/3.0"
            )
        )
        // releaseStream + FCPublish + createStream
        sendCommand(3, 0, "releaseStream", 2.0, amf0Null(), amf0String(stream))
        sendCommand(3, 0, "FCPublish", 3.0, amf0Null(), amf0String(stream))
        sendCommand(3, 0, "createStream", 4.0, amf0Null())

        // 简化：多数 RTMP 服务器首个流的 stream id 为 1
        streamId = 1

        // publish
        sendCommand(3, 0, "publish", 5.0, amf0Null(), amf0String(stream), amf0String("live"))
        // Set Chunk Size = 4096
        sendRawMessage(2, 1, 0, byteArrayOf(0x00, 0x10, 0x00, 0x00))
        return true
    }

    private fun readStreamId(): Int? {
        // 简化：读取若干字节，尝试解析 createStream 的 _result 中的 stream id
        try {
            input?.mark(8192)
            val buf = ByteArray(4096)
            val n = input?.read(buf) ?: return null
            if (n <= 0) return null
            // 在返回数据中查找 createStream 的 _result 后跟的 number
            for (i in 0 until n - 20) {
                // AMF0: "_result"(string) + txid(number) + null + streamId(number)
                if (buf[i] == 0x00.toByte() && buf[i + 1] == 0x00.toByte()) {
                    // 粗略解析：number 后 8 字节为 double
                    val streamId = ((buf[i + 1].toInt() and 0xFF) shl 24) or
                        ((buf[i + 2].toInt() and 0xFF) shl 16) or
                        ((buf[i + 3].toInt() and 0xFF) shl 8) or
                        (buf[i + 4].toInt() and 0xFF)
                    if (streamId > 0) return streamId
                }
            }
            return 1 // 兜底
        } catch (e: Exception) {
            return 1
        }
    }

    // ---- 数据发送 ----
    override fun onSpsPps(sps: ByteArray, pps: ByteArray) {
        this.sps = sps
        this.pps = pps
        if (connected) sendAvcSequenceHeader()
    }

    override fun onVideoFrame(data: ByteArray, ptsUs: Long, isKeyFrame: Boolean) {
        if (!connected || sps == null) return
        val nals = H264Utils.splitAnnexB(data)
        if (nals.isEmpty()) return
        val body = ByteArrayOutputStream()
        for (nal in nals) {
            body.write((nal.size shr 24) and 0xFF)
            body.write((nal.size shr 16) and 0xFF)
            body.write((nal.size shr 8) and 0xFF)
            body.write(nal.size and 0xFF)
            body.write(nal)
        }
        val frameType = if (isKeyFrame) 1 else 2
        val header = byteArrayOf(
            ((frameType shl 4) or 7).toByte(), // codec 7 = H.264
            0x01.toByte(), // AVCPacketType = NALU
            0x00, 0x00, 0x00 // CompositionTime
        )
        sendFlvTag(9, ptsUs / 1000, header + body.toByteArray())
    }

    override fun onAudioConfig(aacConfig: ByteArray, sampleRate: Int, channelCount: Int) {
        this.aacConfig = aacConfig
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        if (connected) sendAacSequenceHeader()
    }

    override fun onAudioFrame(data: ByteArray, ptsUs: Long) {
        if (!connected || aacConfig == null) return
        val header = byteArrayOf(0xAF.toByte(), 0x01.toByte()) // AAC, 44k, 16bit, stereo / raw
        sendFlvTag(8, ptsUs / 1000, header + data)
    }

    private fun sendAvcSequenceHeader() {
        val sps = this.sps ?: return
        val pps = this.pps ?: return
        val body = ByteArrayOutputStream()
        body.write(0x01) // version
        body.write(sps[1].toInt() and 0xFF) // profile
        body.write(sps[2].toInt() and 0xFF) // compat
        body.write(sps[3].toInt() and 0xFF) // level
        body.write(0xFF) // lengthSizeMinusOne=3
        body.write(0xE1) // numSps=1
        body.write((sps.size shr 8) and 0xFF); body.write(sps.size and 0xFF)
        body.write(sps)
        body.write(0x01) // numPps=1
        body.write((pps.size shr 8) and 0xFF); body.write(pps.size and 0xFF)
        body.write(pps)
        val header = byteArrayOf(0x17.toByte(), 0x00.toByte(), 0x00, 0x00, 0x00) // keyframe + H264 + AVC seq header
        sendFlvTag(9, 0, header + body.toByteArray())
    }

    private fun sendAacSequenceHeader() {
        val asc = aacConfig ?: return
        val header = byteArrayOf(0xAF.toByte(), 0x00.toByte()) // AAC seq header
        sendFlvTag(8, 0, header + asc)
    }

    private fun sendFlvHeader() {
        // FLV header: "FLV" + version(1) + flags(5: audio+video) + header size(9)
        val header = byteArrayOf('F'.code.toByte(), 'L'.code.toByte(), 'V'.code.toByte(), 0x01, 0x05, 0x00, 0x00, 0x00, 0x09)
        write(header)
        // PreviousTagSize0
        write(byteArrayOf(0x00, 0x00, 0x00, 0x00))
    }

    private fun sendFlvTag(type: Int, timestampMs: Long, data: ByteArray) {
        val ts = (timestampMs - baseTimeMs).coerceAtLeast(0)
        val header = ByteArray(11)
        header[0] = type.toByte()
        header[1] = ((data.size shr 16) and 0xFF).toByte()
        header[2] = ((data.size shr 8) and 0xFF).toByte()
        header[3] = (data.size and 0xFF).toByte()
        val t = ts.toInt()
        header[4] = ((t shr 16) and 0xFF).toByte()
        header[5] = ((t shr 8) and 0xFF).toByte()
        header[6] = (t and 0xFF).toByte()
        header[7] = ((t shr 24) and 0xFF).toByte()
        header[8] = 0; header[9] = 0; header[10] = 0
        write(header)
        write(data)
        val prevTagSize = data.size + 11
        write(byteArrayOf(((prevTagSize shr 24) and 0xFF).toByte(), ((prevTagSize shr 16) and 0xFF).toByte(), ((prevTagSize shr 8) and 0xFF).toByte(), (prevTagSize and 0xFF).toByte()))
    }

    // ---- RTMP 消息 ----
    private fun sendCommand(csid: Int, streamId: Int, command: String, txid: Double, vararg args: ByteArray) {
        val payload = ByteArrayOutputStream()
        payload.write(amf0String(command))
        payload.write(amf0Number(txid))
        for (a in args) payload.write(a)
        sendRawMessage(csid, 20, streamId, payload.toByteArray()) // 20 = AMF0 command
    }

    private fun sendRawMessage(csid: Int, type: Int, streamId: Int, payload: ByteArray) {
        val out = output ?: return
        // 简化：fmt=0（完整消息头），单块
        val basicHeader = byteArrayOf((csid and 0x3F).toByte()) // fmt=0, csid
        val msgHeader = ByteArray(11)
        val len = payload.size
        // timestamp = 0
        msgHeader[3] = ((len shr 16) and 0xFF).toByte()
        msgHeader[4] = ((len shr 8) and 0xFF).toByte()
        msgHeader[5] = (len and 0xFF).toByte()
        msgHeader[6] = type.toByte()
        msgHeader[7] = ((streamId shr 24) and 0xFF).toByte()
        msgHeader[8] = ((streamId shr 16) and 0xFF).toByte()
        msgHeader[9] = ((streamId shr 8) and 0xFF).toByte()
        msgHeader[10] = (streamId and 0xFF).toByte()
        synchronized(out) {
            out.write(basicHeader)
            out.write(msgHeader)
            out.write(payload)
            out.flush()
        }
    }

    private fun write(data: ByteArray) {
        synchronized(output ?: return) {
            output?.write(data)
            output?.flush()
        }
    }

    private fun readFully(inp: java.io.InputStream, buf: ByteArray) {
        var read = 0
        while (read < buf.size) {
            val r = inp.read(buf, read, buf.size - read)
            if (r == -1) break
            read += r
        }
    }

    private fun parseUrl(url: String): Array<String> {
        // rtmp://host:port/app/stream
        val withoutScheme = url.substringAfter("rtmp://")
        val hostPort = withoutScheme.substringBefore('/')
        val path = withoutScheme.substringAfter('/', "")
        val host = hostPort.substringBefore(':')
        val port = hostPort.substringAfter(':', "1935").toInt()
        val app = path.substringBefore('/')
        val stream = path.substringAfter('/', "")
        return arrayOf(host, port.toString(), app, stream)
    }

    // ---- AMF0 编码 ----
    private fun amf0String(s: String): ByteArray {
        val b = s.toByteArray(Charsets.UTF_8)
        return byteArrayOf(0x02, ((b.size shr 8) and 0xFF).toByte(), (b.size and 0xFF).toByte()) + b
    }

    private fun amf0Number(n: Double): ByteArray {
        val b = ByteArray(9)
        b[0] = 0x00
        val bits = java.lang.Double.doubleToLongBits(n)
        for (i in 0..7) b[i + 1] = ((bits shr ((7 - i) * 8)) and 0xFF).toByte()
        return b
    }

    private fun amf0Boolean(v: Boolean): ByteArray = byteArrayOf(0x01, if (v) 1 else 0)

    private fun amf0Null(): ByteArray = byteArrayOf(0x05)

    private fun amf0Object(vararg entries: Pair<String, Any>): ByteArray {
        val b = ByteArrayOutputStream()
        b.write(0x03)
        for ((k, v) in entries) {
            b.write(amf0String(k))
            when (v) {
                is String -> b.write(amf0String(v))
                is Double -> b.write(amf0Number(v))
                is Boolean -> b.write(amf0Boolean(v))
                else -> b.write(amf0Null())
            }
        }
        b.write(0x00); b.write(0x00); b.write(0x09) // object end
        return b.toByteArray()
    }

    fun close() {
        connected = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        input = null
        output = null
    }

    companion object {
        private const val TAG = "RtmpPusher"
    }
}
