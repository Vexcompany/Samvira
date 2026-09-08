package com.vexcompany.samvira.data.network

/**
 * HTTP request model for [NetworkClient]. Headers/body are explicit so that
 * callers never need to smuggle credentials through URLs.
 */
data class NetworkRequest(
    val method: Method,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
) {
    enum class Method { GET, POST }
}

/** Result of a [NetworkClient] call. */
sealed interface NetworkResult {

    data class Success(
        val statusCode: Int,
        val body: ByteArray,
        val headers: Map<String, String>,
    ) : NetworkResult

    data class Failure(
        val reason: FailureReason,
        val message: String? = null,
    ) : NetworkResult
}

/** Coarse-grained failure taxonomy; callers must fail closed on any of these. */
enum class FailureReason {
    CONNECTIVITY,
    TIMEOUT,
    HTTP_ERROR,
    INVALID_RESPONSE,
    CANCELLED,
    UNKNOWN,
}
