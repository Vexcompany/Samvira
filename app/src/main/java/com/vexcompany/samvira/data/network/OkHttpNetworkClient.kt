package com.vexcompany.samvira.data.network

import com.vexcompany.samvira.core.logging.AppLogger
import com.vexcompany.samvira.core.logging.Scrubber
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OkHttp-backed [NetworkClient].
 *
 * Transport details (timeouts, connection pooling) are centralized here.
 * Logging is limited to the request method and a redacted URL; headers and
 * bodies are never logged.
 */
class OkHttpNetworkClient(
    private val client: OkHttpClient = defaultClient(),
    private val logger: AppLogger? = null,
) : NetworkClient {

    override suspend fun execute(request: NetworkRequest): NetworkResult =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request.toOkHttp()).execute().use { response ->
                    val statusCode = response.code
                    val body = response.body?.bytes() ?: ByteArray(0)
                    val headers = response.headers
                        .toMultimap()
                        .mapValues { (_, values) -> values.joinToString(", ") }

                    if (!response.isSuccessful) {
                        return@withContext NetworkResult.Failure(
                            reason = FailureReason.HTTP_ERROR,
                            message = "HTTP $statusCode",
                        )
                    }

                    NetworkResult.Success(
                        statusCode = statusCode,
                        body = body,
                        headers = headers,
                    )
                }
            } catch (e: IOException) {
                logger?.warn(TAG, "Network request failed: ${Scrubber.redact(request.url)}", e)
                NetworkResult.Failure(FailureReason.CONNECTIVITY, e.message)
            } catch (e: Exception) {
                logger?.error(TAG, "Unexpected network failure.", e)
                NetworkResult.Failure(FailureReason.UNKNOWN, e.message)
            }
        }

    private fun NetworkRequest.toOkHttp(): Request {
        val builder = Request.Builder().url(url)
        headers.forEach { (name, value) -> builder.header(name, value) }
        when (method) {
            NetworkRequest.Method.GET -> builder.get()
            NetworkRequest.Method.POST -> {
                val contentType = headers["Content-Type"]
                    ?.toMediaTypeOrNull()
                    ?: DEFAULT_MEDIA_TYPE
                builder.post((body ?: ByteArray(0)).toRequestBody(contentType))
            }
        }
        return builder.build()
    }

    companion object {
        private const val TAG = "OkHttpNetworkClient"

        private val DEFAULT_MEDIA_TYPE = "application/octet-stream".toMediaTypeOrNull()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 30L
        private const val WRITE_TIMEOUT_SECONDS = 30L
    }
}
