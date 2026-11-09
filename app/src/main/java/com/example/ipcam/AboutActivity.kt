package com.example.ipcam

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
        findViewById<TextView>(R.id.tvVersion).text = "版本 $version"

        findViewById<TextView>(R.id.tvGithub).setOnClickListener {
            openUrl("https://github.com/ispace-top")
        }
        findViewById<TextView>(R.id.tvHomepage).setOnClickListener {
            openUrl("https://ispace.top")
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            // 无浏览器时忽略
        }
    }
}
