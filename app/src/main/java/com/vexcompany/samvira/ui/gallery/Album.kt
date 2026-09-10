package com.vexcompany.samvira.ui.gallery

import com.vexcompany.samvira.domain.media.MediaItem

data class GalleryAlbum(
    val id: String,
    val title: String,
    val subtitle: String,
    val items: List<MediaItem>,
)
