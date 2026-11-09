package top.ispace.wiseeye.keepalive

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager

/**
 * 长时值守锁管理：CPU 不休眠、Wi-Fi 不中断、多播可达。
 */
class LockManager(context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun acquire() {
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IPCam::WakeLock").also { it.acquire() }
        }
        if (wifiLock == null) {
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "IPCam::WifiLock").also { it.acquire() }
        }
        if (multicastLock == null) {
            multicastLock = wifiManager.createMulticastLock("IPCam::MulticastLock").also {
                it.setReferenceCounted(false)
                it.acquire()
            }
        }
    }

    fun release() {
        runCatching { wakeLock?.release() }
        runCatching { wifiLock?.release() }
        runCatching { multicastLock?.release() }
        wakeLock = null
        wifiLock = null
        multicastLock = null
    }
}
