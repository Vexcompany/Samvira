package com.vexcompany.samvira.data.remote

/**
 * Client for the SAMVIRA backend contract, deliberately decoupled from any one
 * transport or backend implementation so the server can evolve or be replaced
 * without touching callers.
 *
 * Implementations must never log authorization headers, session tokens, or
 * request/response bodies.
 */
interface RemoteClient {

    suspend fun register(request: RegisterRequest): ApiResult<RegisterResponse>

    suspend fun requestChallenge(installationId: String): ApiResult<ChallengeResponse>

    suspend fun verify(request: VerifyRequest): ApiResult<VerifyResponse>

    suspend fun revokeSession(sessionToken: String): ApiResult<RevokeSessionResponse>

    /**
     * Lists the organizations this installation belongs to. Requires a valid
     * bearer session; membership claims always come from the server.
     */
    suspend fun listOrganizations(sessionToken: String): ApiResult<OrganizationsResponse> =
        throw UnsupportedOperationException("Organization operations are not implemented by this client")

    /**
     * Fetches the context of a single organization. The client states its
     * intended organization context via the `X-Organization-Id` header, which
     * the server validates against the requested resource — the cross-org
     * isolation boundary.
     */
    suspend fun organizationContext(
        sessionToken: String,
        organizationId: String,
    ): ApiResult<OrganizationContextResponse> =
        throw UnsupportedOperationException("Organization operations are not implemented by this client")
}
