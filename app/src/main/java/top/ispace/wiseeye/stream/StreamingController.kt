package top.ispace.wiseeye.stream

import android.content.Context
import android.view.Surface
import androidx.lifecycle.LifecycleOwner
import top.ispace.wiseeye.data.ConfigStore
import top.ispace.wiseeye.onvif.OnvifServer
import top.ispace.wiseeye.onvif.WsDiscoveryServer
import top.ispace.wiseeye.rtmp.RtmpPusher
import top.ispace.wiseeye.rtsp.RtspServer
import top.ispace.wiseeye.util.NetworkUtil

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
    private var rtmpPusher: RtmpPusher? = null

    @Volatile
    var isStreaming = false
        private set

    var onError: ((String) -> Unit)? = null

    fun start(lifecycleOwner: LifecycleOwner) {
        if (isStreaming) return
        val port = ConfigStore.rtspPort(context)
        val width = ConfigStore.width(context)
        val height = ConfigStore.height(context)
        val fps = ConfigStore.fps(context)
        val bitrate = ConfigStore.bitrate(context)
        val username = ConfigStore.username(context)
        val password = ConfigStore.password(context)
        val rotation = ConfigStore.rotation(context)
        // 竖屏时交换宽高
        val encW = if (rotation == 90) height else width
        val encH = if (rotation == 90) width else height
        try {
            val server = RtspServer(port, username, password)
            server.start()
            rtspServer = server // 立即赋值，异常时 stop() 能正确关闭

            val mux = StreamSinkMultiplexer()
            mux.addVideo(server)
            mux.addAudio(server)

            val rtmpUrl = ConfigStore.rtmpUrl(context)
            if (rtmpUrl.isNotBlank()) {
                val pusher = RtmpPusher()
                if (pusher.connect(rtmpUrl)) {
                    mux.addVideo(pusher)
                    mux.addAudio(pusher)
                    rtmpPusher = pusher
                } else {
                    pusher.close()
                }
            }

            val enc = VideoEncoder()
            val surface = enc.start(encW, encH, fps, bitrate, mux)
            encoder = enc

            val audioEnc = AudioEncoder()
            audioEnc.start(16000, 1, 64_000, mux)
            audioEncoder = audioEnc
            val recorder = AudioRecorder()
            recorder.start(audioEnc)
            audioRecorder = recorder

            val onvif = OnvifServer(rtspPort = port, width = encW, height = encH, fps = fps, username = username, password = password)
            onvif.start()
            onvifServer = onvif
            val ws = WsDiscoveryServer { "http://${NetworkUtil.getLocalIpv4Address() ?: "0.0.0.0"}:8000/onvif/device_service" }
            ws.start()
            wsDiscovery = ws

            val cam = CameraManager(context)
            cam.onZoomReady = { zc -> onvif.setZoomController(zc) }
            cam.bind(surface, lifecycleOwner, rotation = if (rotation == 90) Surface.ROTATION_90 else Surface.ROTATION_0)
            cameraManager = cam

            isStreaming = true
        } catch (e: Exception) {
            onError?.invoke(e.message ?: "start failed")
            stop()
        }
    }

    fun stop() {
        isStreaming = false
        rtmpPusher?.close()
        wsDiscovery?.stop()
        onvifServer?.stop()
        cameraManager?.unbind()
        audioRecorder?.stop()
        audioEncoder?.stop()
        encoder?.stop()
        rtspServer?.stop()
        rtmpPusher = null
        wsDiscovery = null
        onvifServer = null
        cameraManager = null
        audioRecorder = null
        audioEncoder = null
        encoder = null
        rtspServer = null
    }

    fun getRtspUrl(host: String): String {
        val port = ConfigStore.rtspPort(context)
        val u = ConfigStore.username(context)
        val p = ConfigStore.password(context)
        return "rtsp://$u:$p@$host:$port/live"
    }

    fun clientCount(): Int = rtspServer?.clientCount() ?: 0

    fun lastVideoFrameTime(): Long = encoder?.lastFrameTime ?: 0L

    fun switchCamera() {
        cameraManager?.switchCamera()
    }

    fun refreshCamera() {
        cameraManager?.refresh()
    }
}
