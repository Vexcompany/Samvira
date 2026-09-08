package com.vexcompany.samvira.domain.media

sealed interface MediaResult<out T> {
    data class Success<T>(val value: T) : MediaResult<T>
    data class Failure(val code: String, val message: String? = null) : MediaResult<Nothing>
}

interface MediaRepository {
    suspend fun listMedia(sessionToken: String, organizationId: String): MediaResult<List<MediaItem>>
    suspend fun requestView(sessionToken: String, organizationId: String, mediaId: String): MediaResult<MediaViewGrant>
    suspend fun fetchThumbnail(sessionToken: String, organizationId: String, mediaId: String): MediaResult<ByteArray>
    suspend fun fetchContent(sessionToken: String, organizationId: String, mediaId: String, viewToken: String): MediaResult<ByteArray>
}

data class MediaViewGrant(
    val mediaId: String,
    val mediaType: MediaType,
    val mimeType: String,
    val expiresAtEpochMs: Long,
    val contentUrl: String,
    val accessToken: String,
)
