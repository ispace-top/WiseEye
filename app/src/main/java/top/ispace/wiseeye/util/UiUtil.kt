package top.ispace.wiseeye.util

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import top.ispace.wiseeye.data.ConfigStore

/**
 * UI 通用工具：沉浸式全屏 + 输出方向关联。
 */
object UiUtil {

    /** 沉浸式全屏：隐藏状态栏与导航栏，滑动临时显示 */
    fun enableImmersive(activity: Activity) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /** 界面方向跟随旋转设置（横屏/竖屏） */
    fun applyOrientation(activity: Activity) {
        val rotation = ConfigStore.rotation(activity)
        activity.requestedOrientation = if (rotation == 90) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }
}
