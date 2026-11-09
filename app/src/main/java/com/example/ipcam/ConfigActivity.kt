package com.example.ipcam

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ipcam.data.ConfigStore
import com.google.android.material.button.MaterialButton

class ConfigActivity : AppCompatActivity() {

    private val resolutions = listOf(
        "640x480" to (640 to 480),
        "1280x720" to (1280 to 720),
        "1920x1080" to (1920 to 1080)
    )
    private val fpsList = listOf(15, 20, 25, 30)

    private lateinit var spResolution: Spinner
    private lateinit var spFps: Spinner
    private lateinit var etBitrate: EditText
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var etRtmp: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        spResolution = findViewById(R.id.spResolution)
        spFps = findViewById(R.id.spFps)
        etBitrate = findViewById(R.id.etBitrate)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        etRtmp = findViewById(R.id.etRtmp)

        // 分辨率
        spResolution.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, resolutions.map { it.first }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val curW = ConfigStore.width(this)
        val curH = ConfigStore.height(this)
        spResolution.setSelection(resolutions.indexOfFirst { it.second.first == curW && it.second.second == curH }.coerceAtLeast(0))

        // 帧率
        spFps.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fpsList.map { it.toString() })
            .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spFps.setSelection(fpsList.indexOf(ConfigStore.fps(this)).coerceAtLeast(0))

        // 当前值
        etBitrate.setText(ConfigStore.bitrate(this).toString())
        etUsername.setText(ConfigStore.username(this))
        etPassword.setText(ConfigStore.password(this))
        etRtmp.setText(ConfigStore.rtmpUrl(this))

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            val (w, h) = resolutions[spResolution.selectedItemPosition].second
            ConfigStore.setResolution(this, w, h)
            ConfigStore.setFps(this, fpsList[spFps.selectedItemPosition])
            ConfigStore.setBitrate(this, etBitrate.text.toString().toIntOrNull() ?: 2_000_000)
            ConfigStore.setCredentials(this, etUsername.text.toString().trim(), etPassword.text.toString().trim())
            ConfigStore.setRtmpUrl(this, etRtmp.text.toString().trim())
            Toast.makeText(this, "已保存，重新开播后生效", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
