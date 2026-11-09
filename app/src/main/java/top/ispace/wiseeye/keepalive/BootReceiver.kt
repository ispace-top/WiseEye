package top.ispace.wiseeye.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import top.ispace.wiseeye.data.ConfigStore
import top.ispace.wiseeye.stream.StreamService

/**
 * 开机自启：设备重启后，若开启自启开关，则拉起推流前台服务。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!ConfigStore.isAutoStart(context)) return
        Log.i(TAG, "boot completed, auto-starting stream service")
        val svc = Intent(context, StreamService::class.java).apply {
            action = StreamService.ACTION_START
        }
        try {
            ContextCompat.startForegroundService(context, svc)
        } catch (e: Exception) {
            Log.e(TAG, "failed to start stream service on boot", e)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
