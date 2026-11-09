package top.ispace.wiseeye.stream

import java.util.concurrent.CopyOnWriteArrayList

/**
 * 编码输出复用器：将同一路 H.264/AAC 帧分发给多个 sink（如 RTSP 服务端 + RTMP 推流器）。
 */
class StreamSinkMultiplexer : VideoFrameSink, AudioFrameSink {

    private val videoSinks = CopyOnWriteArrayList<VideoFrameSink>()
    private val audioSinks = CopyOnWriteArrayList<AudioFrameSink>()

    fun addVideo(sink: VideoFrameSink) = videoSinks.add(sink)
    fun addAudio(sink: AudioFrameSink) = audioSinks.add(sink)

    override fun onSpsPps(sps: ByteArray, pps: ByteArray) {
        videoSinks.forEach { it.onSpsPps(sps, pps) }
    }

    override fun onVideoFrame(data: ByteArray, ptsUs: Long, isKeyFrame: Boolean) {
        videoSinks.forEach { it.onVideoFrame(data, ptsUs, isKeyFrame) }
    }

    override fun onAudioConfig(aacConfig: ByteArray, sampleRate: Int, channelCount: Int) {
        audioSinks.forEach { it.onAudioConfig(aacConfig, sampleRate, channelCount) }
    }

    override fun onAudioFrame(data: ByteArray, ptsUs: Long) {
        audioSinks.forEach { it.onAudioFrame(data, ptsUs) }
    }
}
