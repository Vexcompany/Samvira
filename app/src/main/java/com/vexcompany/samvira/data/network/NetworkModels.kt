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
        /** HTTP response header names are normalized to lowercase. */
        val headers: Map<String, String>,
    ) : NetworkResult

    data class Failure(
        val reason: FailureReason,
        val message: String? = null,
        /**
         * Present only for [FailureReason.HTTP_ERROR]: the raw response status
         * and body, so the contract layer can decode a structured error
         * envelope. Transport-level failures leave both null.
         */
        val statusCode: Int? = null,
        val body: ByteArray? = null,
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
