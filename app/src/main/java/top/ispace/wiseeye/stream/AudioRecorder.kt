package top.ispace.wiseeye.stream

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

/**
 * 麦克风 PCM 采集（AudioRecord），独立线程读取并喂给 [AudioEncoder]。
 */
class AudioRecorder {

    private var record: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    fun start(encoder: AudioEncoder) {
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufSize = maxOf(minBuf * 2, 4096)
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufSize)
        rec.startRecording()
        record = rec
        running = true
        val startNanos = System.nanoTime()
        thread = Thread({
            val buffer = ByteArray(bufSize)
            while (running) {
                val n = rec.read(buffer, 0, buffer.size)
                if (n > 0) {
                    val ptsUs = (System.nanoTime() - startNanos) / 1000
                    encoder.encodePcm(buffer.copyOf(n), ptsUs)
                }
            }
        }, "AudioRecordThread")
        thread?.start()
        Log.i(TAG, "audio recording started")
    }

    fun stop() {
        running = false
        try {
            thread?.join(500)
        } catch (_: Exception) {
        }
        try {
            record?.stop()
        } catch (_: Exception) {
        }
        try {
            record?.release()
        } catch (_: Exception) {
        }
        record = null
    }

    companion object {
        private const val TAG = "AudioRecorder"
    }
}
