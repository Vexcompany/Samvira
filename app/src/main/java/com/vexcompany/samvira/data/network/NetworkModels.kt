package com.vexcompany.samvira.data.network

data class NetworkRequest(
    val method: Method,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
) {
    enum class Method { GET, POST }
}

sealed interface NetworkResult {
    data class Success(
        val statusCode: Int,
        val body: ByteArray,
        val headers: Map<String, String>,
    ) : NetworkResult

    data class Failure(
        val reason: FailureReason,
        val message: String? = null,
        val statusCode: Int? = null,
        val body: ByteArray? = null,
    ) : NetworkResult
}

enum class FailureReason {
    CONNECTIVITY,
    TIMEOUT,
    HTTP_ERROR,
    INVALID_RESPONSE,
    CANCELLED,
    CONFIGURATION,
    UNKNOWN,
}
