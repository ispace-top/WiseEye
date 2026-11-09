package top.ispace.wiseeye.rtsp

import java.security.MessageDigest

/**
 * RTSP Digest 认证（RFC 2617）。
 */
object DigestAuth {

    fun md5Hex(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    fun generateNonce(): String =
        java.util.UUID.randomUUID().toString().replace("-", "")

    fun verify(
        username: String,
        realm: String,
        password: String,
        method: String,
        uri: String,
        nonce: String,
        response: String
    ): Boolean {
        val ha1 = md5Hex("$username:$realm:$password")
        val ha2 = md5Hex("$method:$uri")
        val expected = md5Hex("$ha1:$nonce:$ha2")
        return expected.equals(response, ignoreCase = true)
    }

    /** 客户端侧计算 Digest 响应（用于本地预览等 RTSP 客户端） */
    fun computeDigest(
        username: String,
        realm: String,
        password: String,
        method: String,
        uri: String,
        nonce: String
    ): String {
        val ha1 = md5Hex("$username:$realm:$password")
        val ha2 = md5Hex("$method:$uri")
        return md5Hex("$ha1:$nonce:$ha2")
    }
}
