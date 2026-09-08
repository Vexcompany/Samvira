package com.vexcompany.samvira.domain.media

/**
 * Provider-neutral media metadata. Original storage details are deliberately
 * absent from this model; SAMVIRA only receives what it is authorized to view.
 */
data class MediaItem(
    val mediaId: String,
    val organizationId: String,
    val type: MediaType,
    val mimeType: String,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val createdAtEpochMs: Long,
    val thumbnailUrl: String?,
)

enum class MediaType { PHOTO, VIDEO }
