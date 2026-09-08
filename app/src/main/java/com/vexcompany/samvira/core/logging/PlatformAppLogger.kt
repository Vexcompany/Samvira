package com.vexcompany.samvira.core.logging

import android.util.Log

/**
 * Default [AppLogger] backed by [android.util.Log].
 *
 * Every message passes through [Scrubber.redact] before it reaches the
 * platform logger, so accidental log statements containing tokens, API keys,
 * passwords, or private keys are neutralized at the boundary.
 */
class PlatformAppLogger : AppLogger {

    override fun debug(tag: String, message: String, throwable: Throwable?) {
        Log.d(tag, Scrubber.redact(message), throwable)
    }

    override fun info(tag: String, message: String, throwable: Throwable?) {
        Log.i(tag, Scrubber.redact(message), throwable)
    }

    override fun warn(tag: String, message: String, throwable: Throwable?) {
        Log.w(tag, Scrubber.redact(message), throwable)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, Scrubber.redact(message), throwable)
    }
}
