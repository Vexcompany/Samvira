package com.vexcompany.samvira.data.media

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** Small private cache for thumbnails; originals are never persisted here. */
class ThumbnailCache(context: Context) {
    private val directory = File(context.cacheDir, "samvira-thumbnails").apply { mkdirs() }

    fun read(organizationId: String, mediaId: String): ByteArray? {
        val file = fileFor(organizationId, mediaId)
        if (!file.isFile) return null
        if (file.length() > MAX_ENTRY_BYTES) {
            runCatching { file.delete() }
            return null
        }
        return runCatching {
            val bytes = file.readBytes()
            file.setLastModified(System.currentTimeMillis())
            bytes
        }.getOrNull()
    }

    fun write(organizationId: String, mediaId: String, bytes: ByteArray) {
        if (bytes.isEmpty() || bytes.size > MAX_ENTRY_BYTES) return
        runCatching {
            val target = fileFor(organizationId, mediaId)
            trimIfNeeded(bytes.size.toLong(), target)
            val temporary = File(directory, "${target.name}.tmp-${Thread.currentThread().id}")
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(target)) {
                temporary.delete()
                target.writeBytes(bytes)
            }
            target.setLastModified(System.currentTimeMillis())
        }
    }

    private fun trimIfNeeded(incomingBytes: Long, replacingFile: File) {
        val replacementSize = if (replacingFile.isFile) replacingFile.length() else 0L
        val files = directory.listFiles()?.filter { it.isFile && it != replacingFile && !it.name.contains(".tmp-") }.orEmpty()
        var total = files.sumOf { it.length() } + replacementSize
        if (total + incomingBytes - replacementSize <= MAX_CACHE_BYTES) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total + incomingBytes - replacementSize <= MAX_CACHE_BYTES) return
            total -= file.length()
            file.delete()
        }
        if (total + incomingBytes - replacementSize > MAX_CACHE_BYTES && replacingFile.isFile) {
            replacingFile.delete()
        }
    }

    private fun fileFor(organizationId: String, mediaId: String): File = File(directory, sha256("$organizationId\u0000$mediaId"))

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_ENTRY_BYTES = 5L * 1024L * 1024L
        const val MAX_CACHE_BYTES = 64L * 1024L * 1024L
    }
}
