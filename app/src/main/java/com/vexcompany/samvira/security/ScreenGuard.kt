package com.vexcompany.samvira.security

import android.app.Activity
import android.os.Build
import android.view.WindowManager

/**
 * Protects sensitive viewer surfaces from OS-level screenshots and screen
 * capture. FLAG_SECURE does not prevent external-camera capture or capture on
 * rooted/modified devices.
 */
object ScreenGuard {

    fun enable(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(false)
        }
    }

    fun disable(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(true)
        }
    }
}
