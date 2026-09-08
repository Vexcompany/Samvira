package com.vexcompany.samvira.data.remote

import com.vexcompany.samvira.core.logging.AppLogger
import com.vexcompany.samvira.data.network.FailureReason
import com.vexcompany.samvira.data.network.NetworkClient
import com.vexcompany.samvira.data.network.NetworkRequest
import com.vexcompany.samvira.data.network.NetworkResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * [RemoteClient] implemented over the existing [NetworkClient] abstraction and
 * the JSON codec.
 *
 * Safety properties:
 *  - The base URL is required; an empty base URL throws at construction so a
 *    build never silently talks to a placeholder endpoint.
 *  - Non-2xx responses are decoded as [ErrorResponse] when possible; otherwise
 *    they surface a stable "HTTP_ERROR" code. Unexpected/malformed payloads
 *    surface "MALFORMED_RESPONSE" and fail closed.
 *  - Only method + path + status are logged (by the network layer); headers,
 *    tokens, and bodies are never logged here.
 */
class HttpRemoteClient(
    private val baseUrl: String,
    private val networkClient: NetworkClient,
    private val json: Json,
    private val logger: AppLogger? = null,
) : RemoteClient {

    init {
        require(baseUrl.isNotBlank()) {
            "API base URL must be configured; refusing to run against an empty endpoint"
        }
    }

    override suspend fun register(request: RegisterRequest): ApiResult<RegisterResponse> {
        val body = encode(request, RegisterRequest.serializer())
        return when (val result = send(NetworkRequest.Method.POST, ApiContract.REGISTER, body)) {
            is ApiResult.Success -> decode(result.value, RegisterResponse.serializer())
            is ApiResult.ApiError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun requestChallenge(installationId: String): ApiResult<ChallengeResponse> {
        val body = encode(ChallengeRequest(installation_id = installationId), ChallengeRequest.serializer())
        return when (val result = send(NetworkRequest.Method.POST, ApiContract.CHALLENGE, body)) {
            is ApiResult.Success -> decode(result.value, ChallengeResponse.serializer())
            is ApiResult.ApiError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun verify(request: VerifyRequest): ApiResult<VerifyResponse> {
        val body = encode(request, VerifyRequest.serializer())
        return when (val result = send(NetworkRequest.Method.POST, ApiContract.VERIFY, body)) {
            is ApiResult.Success -> decode(result.value, VerifyResponse.serializer())
            is ApiResult.ApiError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun revokeSession(sessionToken: String): ApiResult<RevokeSessionResponse> {
        val headers = authHeaders(sessionToken)
        return when (val result = send(NetworkRequest.Method.POST, ApiContract.REVOKE_SESSION, headers = headers)) {
            is ApiResult.Success -> decode(result.value, RevokeSessionResponse.serializer())
            is ApiResult.ApiError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun listOrganizations(sessionToken: String): ApiResult<OrganizationsResponse> {
        return when (val result = send(NetworkRequest.Method.GET, ApiContract.ORGANIZATIONS, headers = authHeaders(sessionToken))) {
            is ApiResult.Success -> decode(result.value, OrganizationsResponse.serializer())
            is ApiResult.ApiError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun organizationContext(
        sessionToken: String,
        organizationId: String,
    ): ApiResult<OrganizationContextResponse> {
        val headers = authHeaders(sessionToken) + mapOf(
            ApiContract.HEADER_ORGANIZATION to organizationId,
        )
        val path = ApiContract.organizationContext(organizationId)
        return when (val result = send(NetworkRequest.Method.GET, path, headers = headers)) {
            is ApiResult.Success -> decode(result.value, OrganizationContextResponse.serializer())
            is ApiResult.ApiError -> result
            is ApiResult.NetworkError -> result
        }
    }

    private fun authHeaders(sessionToken: String): Map<String, String> = mapOf(
        ApiContract.HEADER_AUTHORIZATION to ApiContract.bearer(sessionToken),
    )

    // --- internals ---

    private suspend fun send(
        method: NetworkRequest.Method,
        path: String,
        bodyJson: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): ApiResult<String> {
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
                if (result.statusCode in 200..299) {
                    ApiResult.Success(text)
                } else {
                    parseApiError(text, result.statusCode)
                }
            }
            is NetworkResult.Failure -> {
                if (result.reason == FailureReason.HTTP_ERROR && result.statusCode != null && result.body != null) {
                    parseApiError(String(result.body, Charsets.UTF_8), result.statusCode)
                } else {
                    ApiResult.NetworkError(result.reason)
                }
            }
        }
    }

    private fun <T> encode(value: T, serializer: KSerializer<T>): String =
        json.encodeToString(serializer, value)

    private fun <T> decode(body: String, serializer: KSerializer<T>): ApiResult<T> =
        try {
            ApiResult.Success(json.decodeFromString(serializer, body))
        } catch (e: Exception) {
            logger?.warn(TAG, "Malformed server response (${e.javaClass.simpleName})")
            ApiResult.ApiError("MALFORMED_RESPONSE", "Unexpected server response", null)
        }

    private fun parseApiError(body: String, httpStatus: Int): ApiResult<String> {
        val parsed = try {
            json.decodeFromString(ErrorResponse.serializer(), body)
        } catch (e: Exception) {
            null
        }
        val code = parsed?.error?.code ?: "HTTP_ERROR"
        return ApiResult.ApiError(code, parsed?.error?.message, httpStatus)
    }

    private companion object {
        const val TAG = "HttpRemoteClient"
    }
}
