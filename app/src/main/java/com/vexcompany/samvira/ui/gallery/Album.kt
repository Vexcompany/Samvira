package com.vexcompany.samvira.ui.gallery

import com.vexcompany.samvira.domain.media.MediaItem

/**
 * UI-level album definition. Albums are currently derived from authorized media
 * metadata; no storage/provider-specific album contract is required yet.
 */
data class GalleryAlbum(
    val id: String,
    val title: String,
    val subtitle: String,
    val items: List<MediaItem>,
)
