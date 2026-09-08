package com.vexcompany.samvira.core.logging

/**
 * Logging boundary. All logging in the app funnels through this interface so
 * that sensitive values can be scrubbed in one place and the platform logger
 * can be replaced (or disabled) without touching call sites.
 */
interface AppLogger {
    fun debug(tag: String, message: String, throwable: Throwable? = null)
    fun info(tag: String, message: String, throwable: Throwable? = null)
    fun warn(tag: String, message: String, throwable: Throwable? = null)
    fun error(tag: String, message: String, throwable: Throwable? = null)
}
