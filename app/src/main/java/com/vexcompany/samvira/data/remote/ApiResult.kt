package com.vexcompany.samvira.data.remote

import com.vexcompany.samvira.data.network.FailureReason

/**
 * Result of a [RemoteClient] call.
 *
 * Three possible outcomes, kept explicit so callers fail closed:
 *  - [Success]: the response was parsed into a typed value.
 *  - [ApiError]: the server returned a structured error (or an unparseable
 *    non-2xx payload), carrying the stable error code.
 *  - [NetworkError]: the transport could not complete the request.
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    data class ApiError(
        val code: String,
        val message: String?,
        val httpStatus: Int?,
    ) : ApiResult<Nothing>

    data class NetworkError(val reason: FailureReason) : ApiResult<Nothing>
}
