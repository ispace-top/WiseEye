package com.example.ipcam.stream

import android.content.Context
import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

/**
 * 基于 CameraX 的相机采集，将 Preview 直接输出到编码器输入 Surface（零拷贝）。
 * 同时创建 [ZoomController]，支持多摄光学/数字变焦。
 */
class CameraManager(private val context: Context) {

    private var provider: ProcessCameraProvider? = null
    private var zoomController: ZoomController? = null

    /** 变焦控制器就绪回调（相机绑定后触发） */
    var onZoomReady: ((ZoomController) -> Unit)? = null

    fun bind(surface: Surface, lifecycleOwner: LifecycleOwner, facing: Int = CameraSelector.LENS_FACING_BACK) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val pv = Preview.Builder().build()
                pv.setSurfaceProvider(ContextCompat.getMainExecutor(context)) { request ->
                    request.provideSurface(surface, ContextCompat.getMainExecutor(context)) { }
                }
                val selector = CameraSelector.Builder().requireLensFacing(facing).build()
                p.unbindAll()
                val camera = p.bindToLifecycle(lifecycleOwner, selector, pv)
                zoomController = ZoomControllerFactory.create(camera)
                zoomController?.let { onZoomReady?.invoke(it) }
            } catch (e: Exception) {
                Log.e(TAG, "camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun getZoomController(): ZoomController? = zoomController

    fun unbind() {
        try {
            provider?.unbindAll()
        } catch (_: Exception) {
        }
        zoomController = null
    }

    companion object {
        private const val TAG = "CameraManager"
    }
}
