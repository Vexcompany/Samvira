package com.vexcompany.samvira.data.media

import com.vexcompany.samvira.data.remote.ApiResult
import com.vexcompany.samvira.data.remote.MediaTypeDto
import com.vexcompany.samvira.data.remote.RemoteClient
import com.vexcompany.samvira.domain.media.MediaItem
import com.vexcompany.samvira.domain.media.MediaRepository
import com.vexcompany.samvira.domain.media.MediaResult
import com.vexcompany.samvira.domain.media.MediaType
import com.vexcompany.samvira.domain.media.MediaViewGrant

class RemoteMediaRepository(
    private val remoteClient: RemoteClient,
    private val thumbnailCache: ThumbnailCache,
) : MediaRepository {
    override suspend fun listMedia(sessionToken: String, organizationId: String): MediaResult<List<MediaItem>> = when (val result = remoteClient.listMedia(sessionToken, organizationId)) {
        is ApiResult.Success -> MediaResult.Success(result.value.media.map(::toDomain))
        is ApiResult.ApiError -> MediaResult.Failure(result.code, result.message)
        is ApiResult.NetworkError -> MediaResult.Failure("NETWORK_ERROR")
    }

    override suspend fun requestView(sessionToken: String, organizationId: String, mediaId: String): MediaResult<MediaViewGrant> = when (val result = remoteClient.requestMediaView(sessionToken, organizationId, mediaId)) {
        is ApiResult.Success -> MediaResult.Success(MediaViewGrant(result.value.media_id, toDomainType(result.value.type), result.value.mime_type, result.value.expires_at_epoch_ms, result.value.content_url, result.value.access_token))
        is ApiResult.ApiError -> MediaResult.Failure(result.code, result.message)
        is ApiResult.NetworkError -> MediaResult.Failure("NETWORK_ERROR")
    }

    override suspend fun fetchThumbnail(sessionToken: String, organizationId: String, mediaId: String): MediaResult<ByteArray> {
        thumbnailCache.read(organizationId, mediaId)?.let { return MediaResult.Success(it) }
        return when (val result = remoteClient.fetchMediaThumbnail(sessionToken, organizationId, mediaId)) {
            is ApiResult.Success -> {
                thumbnailCache.write(organizationId, mediaId, result.value)
                MediaResult.Success(result.value)
            }
            is ApiResult.ApiError -> MediaResult.Failure(result.code, result.message)
            is ApiResult.NetworkError -> MediaResult.Failure("NETWORK_ERROR")
        }
    }

    override suspend fun fetchContent(sessionToken: String, organizationId: String, mediaId: String, viewToken: String): MediaResult<ByteArray> =
        when (val result = remoteClient.fetchMediaContent(sessionToken, organizationId, mediaId, viewToken)) {
            is ApiResult.Success -> MediaResult.Success(result.value)
            is ApiResult.ApiError -> MediaResult.Failure(result.code, result.message)
            is ApiResult.NetworkError -> MediaResult.Failure("NETWORK_ERROR")
        }

    private fun toDomain(dto: com.vexcompany.samvira.data.remote.MediaItemDto) = MediaItem(dto.media_id, dto.organization_id, toDomainType(dto.type), dto.mime_type, dto.width, dto.height, dto.duration_ms, dto.created_at_epoch_ms, dto.thumbnail_url)
    private fun toDomainType(type: MediaTypeDto) = when (type) { MediaTypeDto.PHOTO -> MediaType.PHOTO; MediaTypeDto.VIDEO -> MediaType.VIDEO }
}
