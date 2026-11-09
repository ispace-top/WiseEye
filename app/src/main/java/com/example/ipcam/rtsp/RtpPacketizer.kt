package com.example.ipcam.rtsp

import com.example.ipcam.util.H264Utils
import kotlin.random.Random

/**
 * H.264 → RTP 打包器（RFC 6184）。
 * 支持单 NAL 单元包与 FU-A 分片。SSRC 与序号在本实例内共享（M0 简化）。
 */
class RtpPacketizer {

    private val ssrc: Int = Random.nextInt()
    private var seq = 0
    private val mtu = 1400

    /** 将一个 Annex-B 视频帧打包为多个完整 RTP 包（含 12 字节 RTP 头） */
    fun packetizeH264(frame: ByteArray, ptsUs: Long, payloadType: Int = 96): List<ByteArray> {
        val nals = H264Utils.splitAnnexB(frame)
        val packets = mutableListOf<ByteArray>()
        val timestamp = ptsUs * 9 / 100 // us → 90kHz
        for ((idx, nal) in nals.withIndex()) {
            if (nal.isEmpty()) continue
            val isLastNal = idx == nals.lastIndex
            if (nal.size <= mtu) {
                packets.add(concat(buildHeader(payloadType, isLastNal, timestamp), nal))
            } else {
                packets.addAll(fragmentFuA(nal, payloadType, isLastNal, timestamp))
            }
        }
        return packets
    }

    /** 将一个 AAC 帧打包为单个 RTP 包（RFC 3640，2 字节 AU-header） */
    fun packetizeAac(aacFrame: ByteArray, ptsUs: Long, sampleRate: Int, payloadType: Int = 97): List<ByteArray> {
        val timestamp = ptsUs * sampleRate / 1_000_000 // 采样率时钟
        val auHeader = (aacFrame.size shl 3) and 0xFFFF // 13 位 AU 大小 + 3 位索引(0)
        val payload = ByteArray(2 + aacFrame.size)
        payload[0] = ((auHeader shr 8) and 0xFF).toByte()
        payload[1] = (auHeader and 0xFF).toByte()
        System.arraycopy(aacFrame, 0, payload, 2, aacFrame.size)
        return listOf(concat(buildHeader(payloadType, true, timestamp), payload))
    }

    private fun fragmentFuA(nal: ByteArray, pt: Int, isLastNal: Boolean, timestamp: Long): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        val nalHeader = nal[0].toInt() and 0xFF
        val fuIndicator = ((nalHeader and 0xE0) or 28).toByte() // FU-A type = 28
        var offset = 1
        while (offset < nal.size) {
            val remaining = nal.size - offset
            val isLast = remaining <= (mtu - 2)
            val chunkLen = if (isLast) remaining else (mtu - 2)
            val sBit = if (offset == 1) 0x80 else 0
            val eBit = if (isLast) 0x40 else 0
            val fuHeader = (sBit or eBit or (nalHeader and 0x1F)).toByte()
            val payload = ByteArray(2 + chunkLen)
            payload[0] = fuIndicator
            payload[1] = fuHeader
            System.arraycopy(nal, offset, payload, 2, chunkLen)
            val marker = isLastNal && isLast
            out.add(concat(buildHeader(pt, marker, timestamp), payload))
            offset += chunkLen
        }
        return out
    }

    private fun buildHeader(payloadType: Int, marker: Boolean, timestamp: Long): ByteArray {
        val h = ByteArray(12)
        h[0] = 0x80.toByte() // V=2
        h[1] = ((payloadType and 0x7F) or (if (marker) 0x80 else 0)).toByte()
        h[2] = ((seq shr 8) and 0xFF).toByte()
        h[3] = (seq and 0xFF).toByte()
        val ts = timestamp.toInt()
        h[4] = ((ts shr 24) and 0xFF).toByte()
        h[5] = ((ts shr 16) and 0xFF).toByte()
        h[6] = ((ts shr 8) and 0xFF).toByte()
        h[7] = (ts and 0xFF).toByte()
        h[8] = ((ssrc shr 24) and 0xFF).toByte()
        h[9] = ((ssrc shr 16) and 0xFF).toByte()
        h[10] = ((ssrc shr 8) and 0xFF).toByte()
        h[11] = (ssrc and 0xFF).toByte()
        seq++
        return h
    }

    private fun concat(a: ByteArray, b: ByteArray): ByteArray {
        val r = ByteArray(a.size + b.size)
        System.arraycopy(a, 0, r, 0, a.size)
        System.arraycopy(b, 0, r, a.size, b.size)
        return r
    }
}
