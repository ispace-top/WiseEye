package top.ispace.wiseeye

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import top.ispace.wiseeye.data.ConfigStore
import top.ispace.wiseeye.preview.LocalPreview
import top.ispace.wiseeye.stream.StreamService
import top.ispace.wiseeye.util.NetworkUtil
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var svPreview: SurfaceView
    private lateinit var tvStatus: TextView
    private lateinit var tvUrl: TextView
    private lateinit var btnToggle: MaterialButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnAbout: ImageButton
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var btnRotate: ImageButton

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
        btnAbout = findViewById(R.id.btnAbout)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        btnRotate = findViewById(R.id.btnRotate)

        svPreview.holder.addCallback(surfaceCallback)

        btnToggle.setOnClickListener {
            if (streaming) stopStreaming() else requestPermissionAndStart()
        }
        btnSettings.setOnClickListener { startActivity(Intent(this, ConfigActivity::class.java)) }
        btnAbout.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }
        tvUrl.setOnClickListener { copyUrl() }

        btnSwitchCamera.setOnClickListener {
            if (streaming) {
                startService(Intent(this, StreamService::class.java).apply { action = StreamService.ACTION_SWITCH_CAMERA })
                Toast.makeText(this, "已切换摄像头", Toast.LENGTH_SHORT).show()
            }
        }

        btnRotate.setOnClickListener {
            val cur = ConfigStore.rotation(this)
            val newRot = if (cur == 90) 0 else 90
            ConfigStore.setRotation(this, newRot)
            applyOrientation()
            if (streaming) {
                startService(Intent(this, StreamService::class.java).apply { action = StreamService.ACTION_RESTART })
                stopPreview()
                svPreview.postDelayed({ maybeStartPreview() }, 2500)
            }
            Toast.makeText(this, if (newRot == 90) "已切换为竖屏输出" else "已切换为横屏输出", Toast.LENGTH_SHORT).show()
        }

        applyOrientation()
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

    private fun applyOrientation() {
        val rotation = ConfigStore.rotation(this)
        requestedOrientation = if (rotation == 90) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
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
