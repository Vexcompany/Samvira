package com.vexcompany.samvira.data.network

import com.vexcompany.samvira.core.logging.AppLogger
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpNetworkClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpNetworkClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpNetworkClient(client = OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `returns success with status body and headers`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("ok")
                .addHeader("Content-Type", "text/plain"),
        )

        val result = client.execute(
            NetworkRequest(NetworkRequest.Method.GET, server.url("/ping").toString()),
        )

        assertTrue(result is NetworkResult.Success)
        val success = result as NetworkResult.Success
        assertEquals(200, success.statusCode)
        assertEquals("ok", String(success.body))
        assertEquals("text/plain", success.headers["Content-Type"])
    }

    @Test
    fun `maps non-2xx responses to HTTP_ERROR failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("missing"))

        val result = client.execute(
            NetworkRequest(NetworkRequest.Method.GET, server.url("/nope").toString()),
        )

        assertTrue(result is NetworkResult.Failure)
        val failure = result as NetworkResult.Failure
        assertEquals(FailureReason.HTTP_ERROR, failure.reason)
        assertEquals("HTTP 404", failure.message)
    }

    @Test
    fun `maps connection failure to CONNECTIVITY`() = runTest {
        val url = server.url("/unreachable").toString()
        server.shutdown()

        val result = client.execute(NetworkRequest(NetworkRequest.Method.GET, url))

        assertTrue(result is NetworkResult.Failure)
        assertEquals(FailureReason.CONNECTIVITY, (result as NetworkResult.Failure).reason)
    }

    @Test
    fun `maps read timeout to TIMEOUT`() = runTest {
        val timeoutClient = OkHttpNetworkClient(
            client = OkHttpClient.Builder()
                .readTimeout(200, TimeUnit.MILLISECONDS)
                .build(),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("slow response")
                .setBodyDelay(2, TimeUnit.SECONDS),
        )

        val result = timeoutClient.execute(
            NetworkRequest(NetworkRequest.Method.GET, server.url("/slow").toString()),
        )

        assertTrue(result is NetworkResult.Failure)
        assertEquals(FailureReason.TIMEOUT, (result as NetworkResult.Failure).reason)
    }

    @Test
    fun `does not log sensitive query material on failure`() = runTest {
        val logs = mutableListOf<String>()
        val recordingLogger = RecordingLogger(logs)
        val recordingClient = OkHttpNetworkClient(
            client = OkHttpClient(),
            logger = recordingLogger,
        )
        val url = server.url("/secure").toString()
        server.shutdown()

        val result = recordingClient.execute(
            NetworkRequest(
                NetworkRequest.Method.GET,
                "$url?token=SUPERSECRETTOKEN&access_token=ALSO_SECRET",
            ),
        )

        assertTrue(result is NetworkResult.Failure)
        assertTrue(logs.isNotEmpty())
        assertTrue(logs.none { it.contains("SUPERSECRETTOKEN") })
        assertTrue(logs.none { it.contains("ALSO_SECRET") })
        assertTrue(logs.any { it.contains("<redacted>") })
    }

    private class RecordingLogger(private val sink: MutableList<String>) : AppLogger {
        override fun debug(tag: String, message: String, throwable: Throwable?) { sink += message }
        override fun info(tag: String, message: String, throwable: Throwable?) { sink += message }
        override fun warn(tag: String, message: String, throwable: Throwable?) { sink += message }
        override fun error(tag: String, message: String, throwable: Throwable?) { sink += message }
    }
}
