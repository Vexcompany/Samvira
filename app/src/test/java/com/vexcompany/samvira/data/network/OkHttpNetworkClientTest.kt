package com.vexcompany.samvira.data.network

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
            NetworkRequest(
                method = NetworkRequest.Method.GET,
                url = server.url("/ping").toString(),
            ),
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
            NetworkRequest(
                method = NetworkRequest.Method.GET,
                url = server.url("/nope").toString(),
            ),
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

        val result = client.execute(
            NetworkRequest(
                method = NetworkRequest.Method.GET,
                url = url,
            ),
        )

        assertTrue(result is NetworkResult.Failure)
        val failure = result as NetworkResult.Failure
        assertEquals(FailureReason.CONNECTIVITY, failure.reason)
    }
}
