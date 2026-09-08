package com.vexcompany.samvira.data.remote

import kotlinx.serialization.Serializable

// Typed request/response models for the versioned API contract.
// JSON keys are snake_case to match the wire format; kotlinx.serialization maps
// property names 1:1, so keep these field names in sync with the contract.

@Serializable
data class RegisterRequest(
    val installation_id: String,
    val public_key_pem: String,
)

@Serializable
data class RegisterResponse(
    val installation_id: String,
    val registered_at: Long,
    val already_registered: Boolean = false,
)

@Serializable
data class ChallengeRequest(
    val installation_id: String,
)

@Serializable
data class ChallengeResponse(
    val challenge_id: String,
    val nonce_b64: String,
    val expires_at_epoch_ms: Long,
)

@Serializable
data class VerifyRequest(
    val installation_id: String,
    val challenge_id: String,
    val signature_b64: String,
)

@Serializable
data class VerifyResponse(
    val installation_id: String,
    val session_token: String,
    val session_expires_at_epoch_ms: Long,
)

@Serializable
data class RevokeSessionResponse(
    val revoked: Boolean,
)

// --- organizations (Milestone 0.4) ---
// The server is the source of truth for membership authorization. These DTOs
// only mirror what the server returns; membership state is never asserted
// client-side.

@Serializable
data class OrganizationSummaryDto(
    val organization_id: String,
    val name: String,
    val state: String,
)

@Serializable
data class OrganizationsResponse(
    val organizations: List<OrganizationSummaryDto> = emptyList(),
)

@Serializable
data class OrganizationContextResponse(
    val organization_id: String,
    val name: String,
    val state: String,
)

/**
 * Standard error envelope returned with non-2xx responses:
 * `{ "error": { "code": "...", "message": "..." } }`.
 */
@Serializable
data class ErrorResponse(
    val error: ErrorBody,
) {
    @Serializable
    data class ErrorBody(
        val code: String? = null,
        val message: String? = null,
    )
}
