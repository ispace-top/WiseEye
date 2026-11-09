package top.ispace.wiseeye.stream

import androidx.camera.core.Camera

/**
 * 变焦控制器工厂（Factory 模式）。
 * 按厂商/机型/能力选择变焦实现，是厂商适配的扩展点。
 */
object ZoomControllerFactory {

    fun create(camera: Camera): ZoomController {
        // 默认：CameraX 逻辑摄像头（自动镜头切换，覆盖绝大多数设备）。
        // 如需厂商专用实现，可在此按 Build.MANUFACTURER / Build.MODEL / CameraCharacteristics 分支：
        //   - 某些设备在逻辑摄像头之外暴露额外光学变焦 API，可用 Camera2 的
        //     CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE 直接读物理变焦范围并走自定义实现。
        //   - 极老机型（无逻辑摄像头）可降级为纯数字变焦（SCALER_CROP_REGION）。
        return CameraXZoomController(camera)
    }
}
