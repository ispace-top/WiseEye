package top.ispace.wiseeye.util

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtil {

    /**
     * 获取本机第一个非回环 IPv4 地址（用于拼接 RTSP 拉流地址）。
     * 无可用地址时返回 null。
     */
    fun getLocalIpv4Address(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (!ni.isUp || ni.isLoopback) continue
                val addrs = ni.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
