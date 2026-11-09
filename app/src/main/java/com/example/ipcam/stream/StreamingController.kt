package com.example.ipcam.stream

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.example.ipcam.onvif.OnvifServer
import com.example.ipcam.onvif.WsDiscoveryServer
import com.example.ipcam.rtsp.RtspServer
import com.example.ipcam.util.NetworkUtil

/**
 * 流控制中心：编排「相机采集 → 硬编 → RTSP 服务端」全链路。
 */
class StreamingController(private val context: Context) {

    private var encoder: VideoEncoder? = null
    private var audioEncoder: AudioEncoder? = null
    private var audioRecorder: AudioRecorder? = null
    private var rtspServer: RtspServer? = null
    private var cameraManager: CameraManager? = null
    private var onvifServer: OnvifServer? = null
    private var wsDiscovery: WsDiscoveryServer? = null

    @Volatile
    var isStreaming = false
        private set

    var onError: ((String) -> Unit)? = null

    fun start(
        lifecycleOwner: LifecycleOwner,
        port: Int = 8554,
        width: Int = 1280,
        height: Int = 720,
        fps: Int = 25,
        bitrate: Int = 2_000_000
    ) {
        if (isStreaming) return
        try {
            val server = RtspServer(port)
            server.start()
            rtspServer = server // 立即赋值，异常时 stop() 能正确关闭

            val enc = VideoEncoder()
            val surface = enc.start(width, height, fps, bitrate, server)
            encoder = enc

            val audioEnc = AudioEncoder()
            audioEnc.start(16000, 1, 64_000, server)
            audioEncoder = audioEnc
            val recorder = AudioRecorder()
            recorder.start(audioEnc)
            audioRecorder = recorder

            val cam = CameraManager(context)
            cam.bind(surface, lifecycleOwner)
            cameraManager = cam

            val onvif = OnvifServer(rtspPort = port, width = width, height = height, fps = fps)
            onvif.start()
            onvifServer = onvif
            val ws = WsDiscoveryServer { "http://${NetworkUtil.getLocalIpv4Address() ?: "0.0.0.0"}:8000/onvif/device_service" }
            ws.start()
            wsDiscovery = ws

            isStreaming = true
        } catch (e: Exception) {
            onError?.invoke(e.message ?: "start failed")
            stop()
        }
    }

    fun stop() {
        isStreaming = false
        wsDiscovery?.stop()
        onvifServer?.stop()
        cameraManager?.unbind()
        audioRecorder?.stop()
        audioEncoder?.stop()
        encoder?.stop()
        rtspServer?.stop()
        wsDiscovery = null
        onvifServer = null
        cameraManager = null
        audioRecorder = null
        audioEncoder = null
        encoder = null
        rtspServer = null
    }

    fun getRtspUrl(host: String, port: Int = 8554): String = "rtsp://$host:$port/live"
}
