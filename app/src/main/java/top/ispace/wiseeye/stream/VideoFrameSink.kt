package top.ispace.wiseeye.stream

/**
 * 视频编码输出的消费接口。RTSP 服务端实现此接口，接收 SPS/PPS 与 H.264 帧。
 */
interface VideoFrameSink {
    /** 编码器产生 codec-config 时回调（SPS/PPS 为含 NAL header 的完整 NAL 单元） */
    fun onSpsPps(sps: ByteArray, pps: ByteArray)

    /** 每个编码帧回调（Annex-B 格式，可能含多个 NAL 单元） */
    fun onVideoFrame(data: ByteArray, ptsUs: Long, isKeyFrame: Boolean)
}
