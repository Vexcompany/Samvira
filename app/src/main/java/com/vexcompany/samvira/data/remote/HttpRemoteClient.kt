package com.vexcompany.samvira.data.remote

import com.vexcompany.samvira.core.logging.AppLogger
import com.vexcompany.samvira.data.network.FailureReason
import com.vexcompany.samvira.data.network.NetworkClient
import com.vexcompany.samvira.data.network.NetworkRequest
import com.vexcompany.samvira.data.network.NetworkResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** Remote client over the existing NetworkClient abstraction. */
class HttpRemoteClient(
    private val baseUrl: String,
    private val networkClient: NetworkClient,
    private val json: Json,
    private val logger: AppLogger? = null,
) : RemoteClient {

    override suspend fun register(request: RegisterRequest): ApiResult<RegisterResponse> =
        requestResult(ApiContract.REGISTER, NetworkRequest.Method.POST, encode(request, RegisterRequest.serializer()), RegisterResponse.serializer())

    override suspend fun requestChallenge(installationId: String): ApiResult<ChallengeResponse> =
        requestResult(ApiContract.CHALLENGE, NetworkRequest.Method.POST, encode(ChallengeRequest(installation_id = installationId), ChallengeRequest.serializer()), ChallengeResponse.serializer())

    override suspend fun verify(request: VerifyRequest): ApiResult<VerifyResponse> =
        requestResult(ApiContract.VERIFY, NetworkRequest.Method.POST, encode(request, VerifyRequest.serializer()), VerifyResponse.serializer())

    override suspend fun revokeSession(sessionToken: String): ApiResult<RevokeSessionResponse> =
        requestResult(ApiContract.REVOKE_SESSION, NetworkRequest.Method.POST, serializer = RevokeSessionResponse.serializer(), headers = authHeaders(sessionToken))

    override suspend fun listOrganizations(sessionToken: String): ApiResult<OrganizationsResponse> =
        requestResult(ApiContract.ORGANIZATIONS, NetworkRequest.Method.GET, serializer = OrganizationsResponse.serializer(), headers = authHeaders(sessionToken))

    override suspend fun organizationContext(sessionToken: String, organizationId: String): ApiResult<OrganizationContextResponse> =
        requestResult(ApiContract.organizationContext(organizationId), NetworkRequest.Method.GET, serializer = OrganizationContextResponse.serializer(), headers = authHeaders(sessionToken) + (ApiContract.HEADER_ORGANIZATION to organizationId))

    private fun authHeaders(sessionToken: String): Map<String, String> = mapOf(ApiContract.HEADER_AUTHORIZATION to ApiContract.bearer(sessionToken))

    private suspend fun <T> requestResult(
        path: String,
        method: NetworkRequest.Method,
        bodyJson: String? = null,
        serializer: KSerializer<T>,
        headers: Map<String, String> = emptyMap(),
    ): ApiResult<T> {
        if (baseUrl.isBlank()) return ApiResult.NetworkError(FailureReason.CONFIGURATION)
        val request = NetworkRequest(
            method = method,
            url = baseUrl.trimEnd('/') + path,
            headers = buildMap {
                put(ApiContract.HEADER_CONTENT_TYPE, ApiContract.MEDIA_TYPE_JSON)
                putAll(headers)
            },
            body = bodyJson?.toByteArray(),
        )
        return when (val result = networkClient.execute(request)) {
            is NetworkResult.Success -> {
                val text = String(result.body, Charsets.UTF_8)
                if (result.statusCode in 200..299) decode(text, serializer)
                else parseApiError(text, result.statusCode)
            }
            is NetworkResult.Failure -> {
                if (result.reason == FailureReason.HTTP_ERROR && result.statusCode != null && result.body != null) {
                    parseApiError(String(result.body, Charsets.UTF_8), result.statusCode)
                } else ApiResult.NetworkError(result.reason)
            }
        }
    }

    private fun <T> encode(value: T, serializer: KSerializer<T>): String = json.encodeToString(serializer, value)

    private fun <T> decode(body: String, serializer: KSerializer<T>): ApiResult<T> = try {
        ApiResult.Success(json.decodeFromString(serializer, body))
    } catch (e: Exception) {
        logger?.warn(TAG, "Malformed server response (${e.javaClass.simpleName})")
        ApiResult.ApiError("MALFORMED_RESPONSE", "Unexpected server response", null)
    }

    private fun parseApiError(body: String, httpStatus: Int): ApiResult<Nothing> {
        val parsed = try { json.decodeFromString(ErrorResponse.serializer(), body) } catch (_: Exception) { null }
        return ApiResult.ApiError(parsed?.error?.code ?: "HTTP_ERROR", parsed?.error?.message, httpStatus)
    }

    private companion object { const val TAG = "HttpRemoteClient" }
}
