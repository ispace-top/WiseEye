package top.ispace.wiseeye.stream

import androidx.camera.core.Preview

/**
 * 本地预览 SurfaceProvider 持有器。
 * MainActivity 将 PreviewView 的 SurfaceProvider 存于此，供服务端 CameraManager 绑定显示预览。
 */
object PreviewViewProvider {

    @Volatile
    var surfaceProvider: Preview.SurfaceProvider? = null
}
