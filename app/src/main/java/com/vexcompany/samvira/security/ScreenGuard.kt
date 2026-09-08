package com.vexcompany.samvira.security

import android.app.Activity
import android.view.WindowManager

/**
 * Helper for enabling/disabling screenshot protection (FLAG_SECURE) on
 * sensitive viewing surfaces.
 *
 * FLAG_SECURE is a deterrent for in-app screenshots and screen recording; it
 * cannot stop external cameras, rooted/modified devices, or other out-of-band
 * capture. The foundation does not render sensitive media yet, so no surface
 * enables it by default — it is wired here so future viewer screens can opt in
 * per privacy policy.
 */
object ScreenGuard {

    fun enable(activity: Activity) = setSecure(activity, secure = true)

    fun disable(activity: Activity) = setSecure(activity, secure = false)

    private fun setSecure(activity: Activity, secure: Boolean) {
        if (secure) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
