package com.example.ipcam

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.ipcam.data.ConfigStore
import com.example.ipcam.preview.LocalPreview
import com.example.ipcam.stream.StreamService
import com.example.ipcam.util.NetworkUtil
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var svPreview: SurfaceView
    private lateinit var tvStatus: TextView
    private lateinit var tvUrl: TextView
    private lateinit var btnToggle: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var cbAutoStart: CheckBox

    private var preview: LocalPreview? = null
    private var surfaceReady = false
    private var streaming = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val cam = grants[Manifest.permission.CAMERA] == true
            val mic = grants[Manifest.permission.RECORD_AUDIO] == true
            if (cam && mic) startStreaming() else tvStatus.text = "需要相机和麦克风权限"
        }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            surfaceReady = true
            maybeStartPreview()
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            stopPreview()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        svPreview = findViewById(R.id.svPreview)
        tvStatus = findViewById(R.id.tvStatus)
        tvUrl = findViewById(R.id.tvUrl)
        btnToggle = findViewById(R.id.btnToggle)
        btnSettings = findViewById(R.id.btnSettings)
        cbAutoStart = findViewById(R.id.cbAutoStart)

        svPreview.holder.addCallback(surfaceCallback)

        cbAutoStart.isChecked = ConfigStore.isAutoStart(this)
        cbAutoStart.setOnCheckedChangeListener { _, checked -> ConfigStore.setAutoStart(this, checked) }

        btnToggle.setOnClickListener {
            if (streaming) stopStreaming() else requestPermissionAndStart()
        }
        btnSettings.setOnClickListener { startActivity(Intent(this, ConfigActivity::class.java)) }
        tvUrl.setOnClickListener { copyUrl() }

        updateUi()
    }

    override fun onResume() {
        super.onResume()
        streaming = StreamService.isRunning
        updateUi()
        maybeStartPreview()
    }

    override fun onPause() {
        super.onPause()
        stopPreview()
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
        val intent = Intent(this, StreamService::class.java).apply { action = StreamService.ACTION_START }
        ContextCompat.startForegroundService(this, intent)
        streaming = true
        updateUi()
        maybeStartPreview()
    }

    private fun stopStreaming() {
        val intent = Intent(this, StreamService::class.java).apply { action = StreamService.ACTION_STOP }
        startService(intent)
        streaming = false
        stopPreview()
        updateUi()
    }

    private fun maybeStartPreview() {
        if (streaming && surfaceReady) {
            val port = ConfigStore.rtspPort(this)
            val u = ConfigStore.username(this)
            val p = ConfigStore.password(this)
            val url = "rtsp://127.0.0.1:$port/live"
            preview?.stop()
            preview = LocalPreview(svPreview.holder.surface)
            preview?.start(url, u, p)
        }
    }

    private fun stopPreview() {
        preview?.stop()
        preview = null
    }

    private fun copyUrl() {
        val text = tvUrl.text.toString()
        if (text.isEmpty() || text.contains("将显示")) return
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("RTSP URL", text))
        Toast.makeText(this, "RTSP 地址已复制", Toast.LENGTH_SHORT).show()
    }

    private fun updateUi() {
        if (streaming) {
            val ip = NetworkUtil.getLocalIpv4Address() ?: "0.0.0.0"
            val port = ConfigStore.rtspPort(this)
            val u = ConfigStore.username(this)
            val p = ConfigStore.password(this)
            tvStatus.text = "● 推流中"
            tvStatus.setTextColor(0xFF4CAF50.toInt())
            tvUrl.text = "rtsp://$u:$p@$ip:$port/live"
            btnToggle.text = "停止推流"
        } else {
            tvStatus.text = "● 已停止"
            tvStatus.setTextColor(0xFFCCCCCC.toInt())
            tvUrl.text = "RTSP 地址将显示在这里"
            btnToggle.text = "开始推流"
        }
    }
}
