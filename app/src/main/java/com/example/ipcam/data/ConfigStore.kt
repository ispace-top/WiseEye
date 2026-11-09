package com.example.ipcam.data

import android.content.Context

/**
 * 轻量配置存储（SharedPreferences）。
 */
object ConfigStore {

    private const val PREFS = "ipcam_prefs"
    private const val KEY_AUTO_START = "auto_start"

    fun isAutoStart(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_START, false)

    fun setAutoStart(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_START, enabled).apply()
    }
}
