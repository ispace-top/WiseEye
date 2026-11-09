package top.ispace.wiseeye.util

/**
 * H.264 Annex-B 字节流工具。
 * MediaCodec 编码器输出为 Annex-B 格式：每个 NAL 单元前有起始码 00 00 00 01 或 00 00 01。
 */
object H264Utils {

    /**
     * 将 Annex-B 字节流按起始码切分为 NAL 单元（不含起始码，保留 NAL header 字节）。
     */
    fun splitAnnexB(data: ByteArray): List<ByteArray> {
        val nals = mutableListOf<ByteArray>()
        val n = data.size
        var start = -1
        var i = 0
        while (i < n - 2) {
            if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) {
                if (data[i + 2] == 1.toByte()) {
                    // 3 字节起始码 00 00 01
                    if (start >= 0) nals.add(data.copyOfRange(start, i))
                    start = i + 3
                    i += 3
                    continue
                } else if (data[i + 2] == 0.toByte() && i + 3 < n && data[i + 3] == 1.toByte()) {
                    // 4 字节起始码 00 00 00 01
                    if (start >= 0) nals.add(data.copyOfRange(start, i))
                    start = i + 4
                    i += 4
                    continue
                }
            }
            i++
        }
        if (start >= 0 && start < n) nals.add(data.copyOfRange(start, n))
        return nals
    }

    /** NAL 类型（nal header 低 5 位） */
    fun nalType(nal: ByteArray): Int = if (nal.isEmpty()) -1 else (nal[0].toInt() and 0x1F)
}
