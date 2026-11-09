package top.ispace.wiseeye.stream

/**
 * 音频编码输出的消费接口。RTSP 服务端实现此接口，接收 AAC 配置与音频帧。
 */
interface AudioFrameSink {
    /** 编码器产生 codec-config（AudioSpecificConfig）时回调 */
    fun onAudioConfig(aacConfig: ByteArray, sampleRate: Int, channelCount: Int)

    /** 每个 AAC 帧回调（Raw AAC，无 ADTS 头） */
    fun onAudioFrame(data: ByteArray, ptsUs: Long)
}
