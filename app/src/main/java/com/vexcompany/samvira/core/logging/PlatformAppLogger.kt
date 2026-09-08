package com.vexcompany.samvira.core.logging

import android.util.Log

/**
 * Default [AppLogger] backed by [android.util.Log].
 *
 * Messages and throwable text pass through [Scrubber] before reaching logcat,
 * preventing exception messages from becoming an unredacted secret channel.
 */
class PlatformAppLogger : AppLogger {

    override fun debug(tag: String, message: String, throwable: Throwable?) {
        Log.d(tag, Scrubber.redact(message), Scrubber.sanitizeThrowable(throwable))
    }

    override fun info(tag: String, message: String, throwable: Throwable?) {
        Log.i(tag, Scrubber.redact(message), Scrubber.sanitizeThrowable(throwable))
    }

    override fun warn(tag: String, message: String, throwable: Throwable?) {
        Log.w(tag, Scrubber.redact(message), Scrubber.sanitizeThrowable(throwable))
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, Scrubber.redact(message), Scrubber.sanitizeThrowable(throwable))
    }
}
