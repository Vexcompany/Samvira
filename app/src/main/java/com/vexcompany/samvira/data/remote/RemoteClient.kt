package com.vexcompany.samvira.data.remote

/** Client for the versioned SAMVIRA backend contract. */
interface RemoteClient {
    suspend fun register(request: RegisterRequest): ApiResult<RegisterResponse>
    suspend fun requestChallenge(installationId: String): ApiResult<ChallengeResponse>
    suspend fun verify(request: VerifyRequest): ApiResult<VerifyResponse>
    suspend fun revokeSession(sessionToken: String): ApiResult<RevokeSessionResponse>

    suspend fun listOrganizations(sessionToken: String): ApiResult<OrganizationsResponse> =
        throw UnsupportedOperationException("Organization operations are not implemented by this client")

    suspend fun organizationContext(sessionToken: String, organizationId: String): ApiResult<OrganizationContextResponse> =
        throw UnsupportedOperationException("Organization operations are not implemented by this client")

    suspend fun listMedia(sessionToken: String, organizationId: String): ApiResult<MediaListResponse> =
        throw UnsupportedOperationException("Media operations are not implemented by this client")

    suspend fun requestMediaView(sessionToken: String, organizationId: String, mediaId: String): ApiResult<MediaViewResponse> =
        throw UnsupportedOperationException("Media operations are not implemented by this client")

    suspend fun fetchMediaThumbnail(sessionToken: String, organizationId: String, mediaId: String): ApiResult<ByteArray> =
        throw UnsupportedOperationException("Media thumbnail operations are not implemented by this client")

    suspend fun fetchMediaContent(
        sessionToken: String,
        organizationId: String,
        mediaId: String,
        viewToken: String,
    ): ApiResult<ByteArray> =
        throw UnsupportedOperationException("Media content operations are not implemented by this client")
}
