package com.vexcompany.samvira.ui.gallery

import com.vexcompany.samvira.domain.media.MediaItem
import com.vexcompany.samvira.domain.media.MediaType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local, metadata-only discovery over the media already authorized for this organization. */
fun searchMedia(items: List<MediaItem>, query: String): List<MediaItem> {
    val tokens = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+"))
        .filter(String::isNotBlank)
    if (tokens.isEmpty()) return items

    return items.filter { item ->
        val searchable = buildSearchText(item)
        tokens.all { token -> searchable.contains(token) }
    }
}

private fun buildSearchText(item: MediaItem): String {
    val type = when (item.type) {
        MediaType.PHOTO -> "photo image picture"
        MediaType.VIDEO -> "video movie recording"
    }
    val date = Date(item.createdAtEpochMs)
    val dateText = listOf(
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(date),
        SimpleDateFormat("yyyy MMMM MMM", Locale.ENGLISH).format(date),
        SimpleDateFormat("EEEE", Locale.ENGLISH).format(date),
    ).joinToString(" ")
    val dimensions = listOfNotNull(item.width, item.height)
        .takeIf { it.size == 2 }
        ?.let { (width, height) -> "$width $height ${width}x$height" }
        .orEmpty()
    val durationSeconds = item.durationMs?.div(1000)
    val durationText = durationSeconds?.let { "$it ${it}s ${it / 60}m" }.orEmpty()

    return listOf(item.mediaId, item.mimeType, type, dateText, dimensions, durationText)
        .joinToString(" ")
        .lowercase(Locale.ROOT)
}
