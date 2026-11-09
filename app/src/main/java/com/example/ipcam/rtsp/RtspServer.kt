package com.example.ipcam.rtsp

import android.util.Log
import com.example.ipcam.stream.AudioFrameSink
import com.example.ipcam.stream.VideoFrameSink
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * 自研轻量 RTSP 服务端（TCP 监听 + RTP over TCP interleaved，视频/音频双轨）。
 * 实现 [VideoFrameSink] 与 [AudioFrameSink]，接收编码器输出并分发到所有 PLAY 中的会话。
 */
class RtspServer(private val port: Int) : VideoFrameSink, AudioFrameSink {

    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private val sessions = CopyOnWriteArrayList<RtspSession>()
    private val videoPacketizer = RtpPacketizer()
    private val audioPacketizer = RtpPacketizer()
    private val sessionCounter = AtomicInteger(0)

    @Volatile
    private var sps: ByteArray? = null

    @Volatile
    private var pps: ByteArray? = null

    @Volatile
    private var aacConfig: ByteArray? = null

    @Volatile
    private var sampleRate = 16000

    @Volatile
    private var channelCount = 1

    @Volatile
    var running = false
        private set

    fun start() {
        serverSocket = ServerSocket(port)
        running = true
        acceptThread = Thread({
            while (running) {
                try {
                    val socket = serverSocket?.accept() ?: break
                    val session = RtspSession(socket, this, "session-${sessionCounter.incrementAndGet()}")
                    sessions.add(session)
                    Thread { session.run() }.start()
                } catch (e: Exception) {
                    if (!running) break
                }
            }
        }, "RtspAccept")
        acceptThread?.start()
        Log.i(TAG, "RTSP server listening on port $port")
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        sessions.forEach { it.close() }
        sessions.clear()
    }

    fun removeSession(session: RtspSession) {
        sessions.remove(session)
    }

    fun buildSdp(): String = SdpBuilder.build(sps, pps, aacConfig, sampleRate, channelCount)

    override fun onSpsPps(sps: ByteArray, pps: ByteArray) {
        this.sps = sps
        this.pps = pps
    }

    override fun onVideoFrame(data: ByteArray, ptsUs: Long, isKeyFrame: Boolean) {
        if (sessions.isEmpty()) return
        val packets = videoPacketizer.packetizeH264(data, ptsUs)
        if (packets.isEmpty()) return
        for (session in sessions) {
            if (session.isPlaying) {
                for (p in packets) session.sendVideoRtp(p)
            }
        }
    }

    override fun onAudioConfig(aacConfig: ByteArray, sampleRate: Int, channelCount: Int) {
        this.aacConfig = aacConfig
        this.sampleRate = sampleRate
        this.channelCount = channelCount
    }

    override fun onAudioFrame(data: ByteArray, ptsUs: Long) {
        if (sessions.isEmpty()) return
        val packets = audioPacketizer.packetizeAac(data, ptsUs, sampleRate)
        if (packets.isEmpty()) return
        for (session in sessions) {
            if (session.isPlaying) {
                for (p in packets) session.sendAudioRtp(p)
            }
        }
    }

    companion object {
        private const val TAG = "RtspServer"
    }
}
