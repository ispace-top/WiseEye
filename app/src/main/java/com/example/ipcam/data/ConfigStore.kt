package com.example.ipcam.data

import android.content.Context

/**
 * 轻量配置存储（SharedPreferences）。
 */
object ConfigStore {

    private const val PREFS = "ipcam_prefs"

    private const val KEY_AUTO_START = "auto_start"
    private const val KEY_WIDTH = "width"
    private const val KEY_HEIGHT = "height"
    private const val KEY_FPS = "fps"
    private const val KEY_BITRATE = "bitrate"
    private const val KEY_RTSP_PORT = "rtsp_port"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
    private const val KEY_RTMP_URL = "rtmp_url"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAutoStart(context: Context) = prefs(context).getBoolean(KEY_AUTO_START, false)
    fun setAutoStart(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_AUTO_START, v).apply()

    fun width(context: Context) = prefs(context).getInt(KEY_WIDTH, 1280)
    fun height(context: Context) = prefs(context).getInt(KEY_HEIGHT, 720)
    fun setResolution(context: Context, w: Int, h: Int) =
        prefs(context).edit().putInt(KEY_WIDTH, w).putInt(KEY_HEIGHT, h).apply()

    fun fps(context: Context) = prefs(context).getInt(KEY_FPS, 25)
    fun setFps(context: Context, v: Int) = prefs(context).edit().putInt(KEY_FPS, v).apply()

    fun bitrate(context: Context) = prefs(context).getInt(KEY_BITRATE, 2_000_000)
    fun setBitrate(context: Context, v: Int) = prefs(context).edit().putInt(KEY_BITRATE, v).apply()

    fun rtspPort(context: Context) = prefs(context).getInt(KEY_RTSP_PORT, 8554)
    fun setRtspPort(context: Context, v: Int) = prefs(context).edit().putInt(KEY_RTSP_PORT, v).apply()

    fun username(context: Context) = prefs(context).getString(KEY_USERNAME, "admin") ?: "admin"
    fun password(context: Context) = prefs(context).getString(KEY_PASSWORD, "admin123") ?: "admin123"
    fun setCredentials(context: Context, u: String, p: String) =
        prefs(context).edit().putString(KEY_USERNAME, u).putString(KEY_PASSWORD, p).apply()

    fun rtmpUrl(context: Context) = prefs(context).getString(KEY_RTMP_URL, "") ?: ""
    fun setRtmpUrl(context: Context, v: String) = prefs(context).edit().putString(KEY_RTMP_URL, v).apply()
}
