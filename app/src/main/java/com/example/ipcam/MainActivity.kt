package com.example.ipcam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.ipcam.data.ConfigStore
import com.example.ipcam.stream.StreamService
import com.example.ipcam.util.NetworkUtil

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvUrl: TextView
    private lateinit var btnToggle: Button
    private lateinit var cbAutoStart: CheckBox

    // 乐观 UI 状态：start/stop 时立即更新，onResume 时从服务同步
    private var streaming = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val cam = grants[Manifest.permission.CAMERA] == true
            val mic = grants[Manifest.permission.RECORD_AUDIO] == true
            if (cam && mic) startStreaming() else tvStatus.text = "需要相机和麦克风权限"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvUrl = findViewById(R.id.tvUrl)
        btnToggle = findViewById(R.id.btnToggle)
        cbAutoStart = findViewById(R.id.cbAutoStart)

        cbAutoStart.isChecked = ConfigStore.isAutoStart(this)
        cbAutoStart.setOnCheckedChangeListener { _, checked ->
            ConfigStore.setAutoStart(this, checked)
        }

        btnToggle.setOnClickListener {
            if (streaming) stopStreaming() else requestPermissionAndStart()
        }

        updateUi()
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume isRunning=${StreamService.isRunning}")
        streaming = StreamService.isRunning // 同步真实服务状态
        updateUi()
    }

    private fun requestPermissionAndStart() {
        val need = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.RECORD_AUDIO)
        }
        if (need.isEmpty()) startStreaming() else permissionLauncher.launch(need.toTypedArray())
    }

    private fun startStreaming() {
        Log.i(TAG, "startStreaming called")
        val intent = Intent(this, StreamService::class.java).apply { action = StreamService.ACTION_START }
        ContextCompat.startForegroundService(this, intent)
        streaming = true
        updateUi()
    }

    private fun stopStreaming() {
        val intent = Intent(this, StreamService::class.java).apply { action = StreamService.ACTION_STOP }
        startService(intent)
        streaming = false
        updateUi()
    }

    private fun updateUi() {
        if (streaming) {
            val ip = NetworkUtil.getLocalIpv4Address() ?: "0.0.0.0"
            tvStatus.text = "推流中"
            tvUrl.text = "rtsp://$ip:8554/live"
            btnToggle.text = "停止"
        } else {
            tvStatus.text = "已停止"
            tvUrl.text = "RTSP 地址将显示在这里"
            btnToggle.text = "开始推流"
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
