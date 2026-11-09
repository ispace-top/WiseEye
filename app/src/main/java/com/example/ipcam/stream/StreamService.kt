package com.example.ipcam.stream

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.example.ipcam.MainActivity
import com.example.ipcam.R
import com.example.ipcam.keepalive.LockManager

/**
 * 前台服务：承载推流全链路，屏幕关闭/应用退后台时持续运行。
 * 内置看门狗：检测编码帧心跳，异常时自动重启推流。
 */
class StreamService : LifecycleService() {

    private lateinit var controller: StreamingController
    private lateinit var lockManager: LockManager
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        controller = StreamingController(this)
        controller.onError = { msg -> Log.e(TAG, "streaming error: $msg") }
        lockManager = LockManager(this)
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0))
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_SWITCH_CAMERA -> controller.switchCamera()
            ACTION_RESTART -> restartStreaming()
            else -> startStreaming() // ACTION_START 或系统重启（intent == null）
        }
        return START_STICKY
    }

    private fun startStreaming() {
        if (controller.isStreaming) return
        lockManager.acquire()
        controller.start(this)
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, WATCHDOG_INTERVAL)
        Log.i(TAG, "streaming started")
    }

    private fun restartStreaming() {
        controller.stop()
        startStreaming()
        Log.i(TAG, "streaming restarted")
    }

    private val watchdog = object : Runnable {
        override fun run() {
            if (controller.isStreaming) {
                val last = controller.lastVideoFrameTime()
                if (last > 0 && System.currentTimeMillis() - last > WATCHDOG_STALL_MS) {
                    Log.w(TAG, "看门狗：${WATCHDOG_STALL_MS / 1000}s 无视频帧，重启推流")
                    controller.stop()
                    lockManager.acquire()
                    controller.start(this@StreamService)
                }
                updateNotification()
            }
            handler.postDelayed(this, WATCHDOG_INTERVAL)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(watchdog)
        isRunning = false
        controller.stop()
        lockManager.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "推流服务", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val notif = buildNotification(controller.clientCount())
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notif)
    }

    private fun buildNotification(clientCount: Int): Notification {
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, StreamService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        val contentPi = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (clientCount > 0) "RTSP 服务运行中 · $clientCount 路连接" else "RTSP 服务运行中"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("${getString(R.string.app_name)} 推流中")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .setContentIntent(contentPi)
            .addAction(0, "停止", stopPi)
            .build()
    }

    companion object {
        private const val TAG = "StreamService"
        private const val CHANNEL_ID = "ipcam_stream"
        private const val NOTIFICATION_ID = 1
        private const val WATCHDOG_INTERVAL = 10_000L
        private const val WATCHDOG_STALL_MS = 15_000L

        const val ACTION_START = "com.example.ipcam.action.START"
        const val ACTION_STOP = "com.example.ipcam.action.STOP"
        const val ACTION_SWITCH_CAMERA = "com.example.ipcam.action.SWITCH_CAMERA"
        const val ACTION_RESTART = "com.example.ipcam.action.RESTART"

        @Volatile
        var isRunning = false
            private set
    }
}
