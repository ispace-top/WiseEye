package com.example.ipcam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.ipcam.stream.StreamingController
import com.example.ipcam.util.NetworkUtil

class MainActivity : AppCompatActivity() {

    private lateinit var controller: StreamingController
    private lateinit var tvStatus: TextView
    private lateinit var tvUrl: TextView
    private lateinit var btnToggle: Button

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
        controller = StreamingController(this)
        controller.onError = { msg -> runOnUiThread { tvStatus.text = "错误: $msg" } }

        btnToggle.setOnClickListener {
            if (controller.isStreaming) {
                controller.stop()
                updateUi()
            } else {
                requestPermissionAndStart()
            }
        }

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
        controller.start(this)
        updateUi()
    }

    private fun updateUi() {
        if (controller.isStreaming) {
            val ip = NetworkUtil.getLocalIpv4Address() ?: "0.0.0.0"
            tvStatus.text = "推流中"
            tvUrl.text = controller.getRtspUrl(ip)
            btnToggle.text = "停止"
        } else {
            tvStatus.text = "已停止"
            tvUrl.text = "RTSP 地址将显示在这里"
            btnToggle.text = "开始推流"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controller.stop()
    }
}
