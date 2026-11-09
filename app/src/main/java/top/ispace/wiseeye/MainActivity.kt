package top.ispace.wiseeye

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import top.ispace.wiseeye.data.ConfigStore
import top.ispace.wiseeye.stream.PreviewViewProvider
import top.ispace.wiseeye.stream.StreamService
import top.ispace.wiseeye.util.NetworkUtil
import top.ispace.wiseeye.util.UiUtil

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var tvStatus: TextView
    private lateinit var tvUrl: TextView
    private lateinit var btnToggle: MaterialButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnAbout: ImageButton
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var btnRotate: ImageButton

    private var streaming = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val cam = grants[Manifest.permission.CAMERA] == true
            val mic = grants[Manifest.permission.RECORD_AUDIO] == true
            if (cam && mic) startStreaming() else tvStatus.text = "需要相机和麦克风权限"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiUtil.enableImmersive(this)
        UiUtil.applyOrientation(this)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        tvStatus = findViewById(R.id.tvStatus)
        tvUrl = findViewById(R.id.tvUrl)
        btnToggle = findViewById(R.id.btnToggle)
        btnSettings = findViewById(R.id.btnSettings)
        btnAbout = findViewById(R.id.btnAbout)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        btnRotate = findViewById(R.id.btnRotate)

        // 将 PreviewView 的 SurfaceProvider 交给服务端 CameraManager，用于本地预览
        PreviewViewProvider.surfaceProvider = previewView.surfaceProvider

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
            UiUtil.applyOrientation(this)
            if (streaming) {
                startService(Intent(this, StreamService::class.java).apply { action = StreamService.ACTION_RESTART })
            }
            Toast.makeText(this, if (newRot == 90) "已切换为竖屏输出" else "已切换为横屏输出", Toast.LENGTH_SHORT).show()
        }

        updateUi()
    }

    override fun onResume() {
        super.onResume()
        streaming = StreamService.isRunning
        PreviewViewProvider.surfaceProvider = previewView.surfaceProvider
        if (streaming) {
            // 重新绑定相机，恢复本地预览
            startService(Intent(this, StreamService::class.java).apply { action = StreamService.ACTION_REFRESH_PREVIEW })
        }
        updateUi()
    }

    override fun onPause() {
        super.onPause()
        // 熄屏/后台时移除显示预览，仅保留编码器推流，避免相机帧率下降
        PreviewViewProvider.surfaceProvider = null
        if (streaming) {
            startService(Intent(this, StreamService::class.java).apply { action = StreamService.ACTION_REFRESH_PREVIEW })
        }
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
    }

    private fun stopStreaming() {
        val intent = Intent(this, StreamService::class.java).apply { action = StreamService.ACTION_STOP }
        startService(intent)
        streaming = false
        updateUi()
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
