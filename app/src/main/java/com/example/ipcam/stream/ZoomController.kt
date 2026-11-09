package com.example.ipcam.stream

/**
 * 变焦控制抽象（Strategy 模式）。
 * 不同厂商/机型的多摄变焦（光学镜头切换 + 数字变焦）在此接口后适配。
 */
interface ZoomController {
    /** 最大变焦倍率（1.0 = 无变焦） */
    val maxZoomRatio: Float

    /** 当前变焦倍率 */
    val currentZoomRatio: Float

    /** 设置绝对变焦倍率（自动 clamp 到 [1.0, maxZoomRatio]） */
    fun setZoomRatio(ratio: Float): Boolean

    /** 相对变焦（delta 为倍率增量，正=放大，负=缩小） */
    fun zoomBy(delta: Float): Boolean

    /** 连续变焦（velocity 正=放大，负=缩小），直到 [stopContinuousZoom] */
    fun startContinuousZoom(velocity: Float)

    /** 停止连续变焦 */
    fun stopContinuousZoom()
}
