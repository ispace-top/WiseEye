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
 */
class CameraManager(private val context: Context) {

    private var provider: ProcessCameraProvider? = null

    fun bind(surface: Surface, lifecycleOwner: LifecycleOwner, facing: Int = CameraSelector.LENS_FACING_BACK) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(ContextCompat.getMainExecutor(context)) { request ->
                    request.provideSurface(surface, ContextCompat.getMainExecutor(context)) { }
                }
                val selector = CameraSelector.Builder().requireLensFacing(facing).build()
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, selector, preview)
            } catch (e: Exception) {
                Log.e(TAG, "camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun unbind() {
        try {
            provider?.unbindAll()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "CameraManager"
    }
}
