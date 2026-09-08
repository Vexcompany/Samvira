package com.vexcompany.samvira.data.remote

import com.vexcompany.samvira.data.network.OkHttpNetworkClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpRemoteClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: HttpRemoteClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = HttpRemoteClient(
            baseUrl = server.url("/").toString(),
            networkClient = OkHttpNetworkClient(client = OkHttpClient()),
            json = Json { ignoreUnknownKeys = true },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `register returns typed response on 2xx`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"installation_id":"abc","registered_at":1000,"already_registered":false}""",
                ),
        )

        val result = client.register(
            RegisterRequest(installation_id = "abc", public_key_pem = "-----BEGIN PUBLIC KEY-----"),
        )

        assertTrue(result is ApiResult.Success)
        val success = result as ApiResult.Success
        assertEquals("abc", success.value.installation_id)
        assertEquals(1000L, success.value.registered_at)
        assertEquals(false, success.value.already_registered)
    }

    @Test
    fun `register maps structured error envelope to ApiError`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setBody("""{"error":{"code":"INSTALLATION_CONFLICT","message":"conflict"}}"""),
        )

        val result = client.register(
            RegisterRequest(installation_id = "abc", public_key_pem = "-----BEGIN PUBLIC KEY-----"),
        )

        assertTrue(result is ApiResult.ApiError)
        val error = result as ApiResult.ApiError
        assertEquals("INSTALLATION_CONFLICT", error.code)
        assertEquals(409, error.httpStatus)
    }

    @Test
    fun `maps unparseable non-2xx body to HTTP_ERROR`() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>bad gateway</html>"))

        val result = client.register(
            RegisterRequest(installation_id = "abc", public_key_pem = "-----BEGIN PUBLIC KEY-----"),
        )

        assertTrue(result is ApiResult.ApiError)
        val error = result as ApiResult.ApiError
        assertEquals("HTTP_ERROR", error.code)
        assertEquals(502, error.httpStatus)
    }

    @Test
    fun `maps malformed 2xx body to MALFORMED_RESPONSE`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("this is not json"))

        val result = client.register(
            RegisterRequest(installation_id = "abc", public_key_pem = "-----BEGIN PUBLIC KEY-----"),
        )

        assertTrue(result is ApiResult.ApiError)
        val error = result as ApiResult.ApiError
        assertEquals("MALFORMED_RESPONSE", error.code)
        assertEquals(null, error.httpStatus)
    }

    @Test
    fun `verify returns session on 2xx`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"installation_id":"abc","session_token":"tok","session_expires_at_epoch_ms":2000}""",
                ),
        )

        val result = client.verify(
            VerifyRequest(
                installation_id = "abc",
                challenge_id = "ch1",
                signature_b64 = "c2ln",
            ),
        )

        assertTrue(result is ApiResult.Success)
        val success = result as ApiResult.Success
        assertEquals("tok", success.value.session_token)
        assertEquals(2000L, success.value.session_expires_at_epoch_ms)
    }

    @Test
    fun `maps transport failure to NetworkError`() = runTest {
        val url = server.url("/").toString()
        server.shutdown()

        val offline = HttpRemoteClient(
            baseUrl = url,
            networkClient = OkHttpNetworkClient(client = OkHttpClient()),
            json = Json { ignoreUnknownKeys = true },
        )

        val result = offline.register(
            RegisterRequest(installation_id = "abc", public_key_pem = "-----BEGIN PUBLIC KEY-----"),
        )

        assertTrue(result is ApiResult.NetworkError)
    }

    @Test
    fun `revoke sends bearer authorization header`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"revoked":true}"""))

        val result = client.revokeSession("session-token-123")

        assertTrue(result is ApiResult.Success)
        val recorded = server.takeRequest()
        assertEquals("Bearer session-token-123", recorded.getHeader("Authorization"))
        assertEquals("POST", recorded.method)
        assertEquals("/api/v1/session/revoke", recorded.path)
    }

    @Test
    fun `listOrganizations sends bearer header and parses response`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"organizations":[{"organization_id":"org-1","name":"One","state":"ACTIVE"}]}""",
                ),
        )

        val result = client.listOrganizations("session-token-123")

        assertTrue(result is ApiResult.Success)
        val orgs = (result as ApiResult.Success).value.organizations
        assertEquals(1, orgs.size)
        assertEquals("org-1", orgs[0].organization_id)
        assertEquals("ACTIVE", orgs[0].state)

        val recorded = server.takeRequest()
        assertEquals("Bearer session-token-123", recorded.getHeader("Authorization"))
        assertEquals("GET", recorded.method)
        assertEquals("/api/v1/organizations", recorded.path)
    }

    @Test
    fun `organizationContext sends X-Organization-Id header`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"organization_id":"org-1","name":"One","state":"ACTIVE"}"""),
        )

        val result = client.organizationContext("session-token-123", "org-1")

        assertTrue(result is ApiResult.Success)
        assertEquals("org-1", (result as ApiResult.Success).value.organization_id)

        val recorded = server.takeRequest()
        assertEquals("Bearer session-token-123", recorded.getHeader("Authorization"))
        assertEquals("org-1", recorded.getHeader("X-Organization-Id"))
        assertEquals("/api/v1/organizations/org-1/context", recorded.path)
    }
}
