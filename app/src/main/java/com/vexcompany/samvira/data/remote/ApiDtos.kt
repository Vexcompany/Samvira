package com.vexcompany.samvira.data.remote

import kotlinx.serialization.Serializable

@Serializable data class RegisterRequest(val installation_id: String, val public_key_pem: String)
@Serializable data class RegisterResponse(val installation_id: String, val registered_at: Long, val already_registered: Boolean = false)
@Serializable data class ChallengeRequest(val installation_id: String)
@Serializable data class ChallengeResponse(val challenge_id: String, val nonce_b64: String, val expires_at_epoch_ms: Long)
@Serializable data class VerifyRequest(val installation_id: String, val challenge_id: String, val signature_b64: String)
@Serializable data class VerifyResponse(val installation_id: String, val session_token: String, val session_expires_at_epoch_ms: Long)
@Serializable data class RevokeSessionResponse(val revoked: Boolean)
@Serializable data class OrganizationSummaryDto(val organization_id: String, val name: String, val state: String)
@Serializable data class OrganizationsResponse(val organizations: List<OrganizationSummaryDto> = emptyList())
@Serializable data class OrganizationContextResponse(val organization_id: String, val name: String, val state: String)
@Serializable enum class MediaTypeDto { PHOTO, VIDEO }
@Serializable data class MediaItemDto(val media_id: String, val organization_id: String, val type: MediaTypeDto, val mime_type: String, val width: Int? = null, val height: Int? = null, val duration_ms: Long? = null, val created_at_epoch_ms: Long, val thumbnail_url: String? = null)
@Serializable data class MediaListResponse(val media: List<MediaItemDto> = emptyList())
@Serializable data class MediaViewResponse(val media_id: String, val type: MediaTypeDto, val mime_type: String, val expires_at_epoch_ms: Long, val content_url: String, val access_token: String)
@Serializable data class ErrorResponse(val error: ErrorBody) { @Serializable data class ErrorBody(val code: String? = null, val message: String? = null) }
