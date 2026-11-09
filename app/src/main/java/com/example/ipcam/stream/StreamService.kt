package com.example.ipcam.stream

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.example.ipcam.MainActivity
import com.example.ipcam.R
import com.example.ipcam.keepalive.LockManager

/**
 * 前台服务：承载推流全链路，屏幕关闭/应用退后台时持续运行。
 */
class StreamService : LifecycleService() {

    private lateinit var controller: StreamingController
    private lateinit var lockManager: LockManager

    override fun onCreate() {
        super.onCreate()
        controller = StreamingController(this)
        controller.onError = { msg -> Log.e(TAG, "streaming error: $msg") }
        lockManager = LockManager(this)
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            else -> startStreaming() // ACTION_START 或系统重启（intent == null）
        }
        return START_STICKY
    }

    private fun startStreaming() {
        if (controller.isStreaming) return
        lockManager.acquire()
        controller.start(this)
        Log.i(TAG, "streaming started")
    }

    override fun onDestroy() {
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

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, StreamService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPi = PendingIntent.getActivity(this, 1, contentIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IPCam 推流中")
            .setContentText("RTSP 服务运行中")
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

        const val ACTION_START = "com.example.ipcam.action.START"
        const val ACTION_STOP = "com.example.ipcam.action.STOP"

        @Volatile
        var isRunning = false
            private set
    }
}
