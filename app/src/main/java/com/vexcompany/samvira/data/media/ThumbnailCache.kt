package com.vexcompany.samvira.data.media

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** Small private cache for thumbnails; originals are never persisted here. */
class ThumbnailCache(context: Context) {
    private val directory = File(context.cacheDir, "samvira-thumbnails").apply { mkdirs() }

    fun read(mediaId: String): ByteArray? {
        val file = fileFor(mediaId)
        return if (file.isFile && file.length() <= MAX_ENTRY_BYTES) runCatching { file.readBytes() }.getOrNull() else null
    }

    fun write(mediaId: String, bytes: ByteArray) {
        if (bytes.isEmpty() || bytes.size > MAX_ENTRY_BYTES) return
        runCatching {
            trimIfNeeded(bytes.size.toLong())
            val file = fileFor(mediaId)
            file.writeBytes(bytes)
        }
    }

    private fun trimIfNeeded(incomingBytes: Long) {
        val files = directory.listFiles()?.filter { it.isFile }.orEmpty()
        var total = files.sumOf { it.length() }
        if (total + incomingBytes <= MAX_CACHE_BYTES) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total + incomingBytes <= MAX_CACHE_BYTES) return
            total -= file.length()
            file.delete()
        }
    }

    private fun fileFor(mediaId: String): File = File(directory, sha256(mediaId))

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_ENTRY_BYTES = 5L * 1024L * 1024L
        const val MAX_CACHE_BYTES = 64L * 1024L * 1024L
    }
}
