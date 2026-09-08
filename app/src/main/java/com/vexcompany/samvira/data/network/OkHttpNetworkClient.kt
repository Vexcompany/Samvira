package com.vexcompany.samvira.data.network

import com.vexcompany.samvira.core.logging.AppLogger
import com.vexcompany.samvira.core.logging.Scrubber
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
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
 * Transport details are centralized here. Headers and bodies are never logged;
 * only a scrubbed URL is included in diagnostic messages. Failure messages are
 * fixed and data-free so raw exception text cannot leak to callers.
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
            } catch (e: SocketTimeoutException) {
                logger?.warn(TAG, "Request timed out: ${Scrubber.redact(request.url)}")
                NetworkResult.Failure(FailureReason.TIMEOUT, "timed out")
            } catch (e: InterruptedIOException) {
                logger?.warn(TAG, "Request cancelled: ${Scrubber.redact(request.url)}")
                NetworkResult.Failure(FailureReason.CANCELLED, "cancelled")
            } catch (e: IOException) {
                logger?.warn(
                    TAG,
                    "Request failed (${e.javaClass.simpleName}): ${Scrubber.redact(request.url)}",
                )
                NetworkResult.Failure(FailureReason.CONNECTIVITY, "connection failed")
            } catch (e: Exception) {
                logger?.error(TAG, "Unexpected network failure (${e.javaClass.simpleName}).")
                NetworkResult.Failure(FailureReason.UNKNOWN, "unexpected error")
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
