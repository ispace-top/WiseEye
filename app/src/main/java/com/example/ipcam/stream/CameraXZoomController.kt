package com.example.ipcam.stream

import androidx.camera.core.Camera

/**
 * 基于 CameraX 逻辑摄像头的变焦实现（默认策略）。
 * CameraX 的 setZoomRatio 会在多摄（广角/长焦）间自动切换镜头实现光学变焦。
 */
class CameraXZoomController(private val camera: Camera) : ZoomController {

    override val maxZoomRatio: Float
        get() = camera.cameraInfo.zoomState.value?.maxZoomRatio ?: 1.0f

    override val currentZoomRatio: Float
        get() = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1.0f

    override fun setZoomRatio(ratio: Float): Boolean {
        val max = maxZoomRatio
        if (max <= 1.0f) return false
        camera.cameraControl.setZoomRatio(ratio.coerceIn(1.0f, max))
        return true
    }

    override fun zoomBy(delta: Float): Boolean =
        setZoomRatio(currentZoomRatio + delta)

    @Volatile
    private var zooming = false

    private var zoomThread: Thread? = null

    override fun startContinuousZoom(velocity: Float) {
        if (velocity == 0f) {
            stopContinuousZoom()
            return
        }
        if (zooming) return
        zooming = true
        zoomThread = Thread({
            while (zooming) {
                setZoomRatio(currentZoomRatio + velocity * 0.03f)
                try {
                    Thread.sleep(80)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }, "ZoomThread").also { it.start() }
    }

    override fun stopContinuousZoom() {
        zooming = false
        zoomThread?.interrupt()
        zoomThread = null
    }
}
