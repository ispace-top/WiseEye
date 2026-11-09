package top.ispace.wiseeye.stream

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
 * 支持前/后摄切换与输出方向旋转，同时创建 [ZoomController]。
 */
class CameraManager(private val context: Context) {

    private var provider: ProcessCameraProvider? = null
    private var zoomController: ZoomController? = null
    private var surface: Surface? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var facing = CameraSelector.LENS_FACING_BACK
    private var rotation = Surface.ROTATION_0

    /** 变焦控制器就绪回调（相机绑定后触发） */
    var onZoomReady: ((ZoomController) -> Unit)? = null

    fun bind(
        surface: Surface,
        lifecycleOwner: LifecycleOwner,
        facing: Int = CameraSelector.LENS_FACING_BACK,
        rotation: Int = Surface.ROTATION_0
    ) {
        this.surface = surface
        this.lifecycleOwner = lifecycleOwner
        this.facing = facing
        this.rotation = rotation
        doBind()
    }

    /** 切换前/后摄像头 */
    fun switchCamera() {
        facing = if (facing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        doBind()
    }

    /** 设置输出方向旋转 */
    fun setRotation(rotation: Int) {
        this.rotation = rotation
        doBind()
    }

    fun currentFacing(): Int = facing

    private fun doBind() {
        val s = surface ?: return
        val lo = lifecycleOwner ?: return
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val pv = Preview.Builder().build()
                pv.setTargetRotation(rotation)
                pv.setSurfaceProvider(ContextCompat.getMainExecutor(context)) { request ->
                    request.provideSurface(s, ContextCompat.getMainExecutor(context)) { }
                }
                val selector = CameraSelector.Builder().requireLensFacing(facing).build()
                p.unbindAll()
                val camera = p.bindToLifecycle(lo, selector, pv)
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
        surface = null
        lifecycleOwner = null
        zoomController = null
    }

    companion object {
        private const val TAG = "CameraManager"
    }
}
