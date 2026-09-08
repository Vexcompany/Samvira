package com.vexcompany.samvira.domain.auth

import com.vexcompany.samvira.data.auth.SessionRecord
import com.vexcompany.samvira.data.auth.SessionStore
import com.vexcompany.samvira.data.remote.ApiResult
import com.vexcompany.samvira.data.remote.ChallengeResponse
import com.vexcompany.samvira.data.remote.RegisterRequest
import com.vexcompany.samvira.data.remote.RegisterResponse
import com.vexcompany.samvira.data.remote.RemoteClient
import com.vexcompany.samvira.data.remote.VerifyRequest
import com.vexcompany.samvira.data.remote.VerifyResponse
import com.vexcompany.samvira.domain.identity.InstallationIdentity
import com.vexcompany.samvira.domain.identity.InstallationIdentityRepository
import com.vexcompany.samvira.security.keystore.InstallKeyStore
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAuthRepositoryTest {

    private val now = 1_700_000_000_000L
    private val future = now + 60_000L
    private val nonceB64 = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(ByteArray(32) { it.toByte() })

    @Test
    fun `establishes session end to end and persists it`() = runTest {
        val store = FakeSessionStore()
        val remote = FakeRemoteClient(
            registerResult = { ApiResult.Success(RegisterResponse("inst-1", now, false)) },
            challengeResult = {
                ApiResult.Success(ChallengeResponse("ch-1", nonceB64, now + 30_000L))
            },
            verifyResult = {
                ApiResult.Success(VerifyResponse("inst-1", "session-token", now + 60_000L))
            },
        )
        val repository = DefaultAuthRepository(
            identityRepository = FakeIdentityRepository(),
            keyStore = FakeKeyStore(),
            remoteClient = remote,
            sessionStore = store,
            clock = { now },
        )

        val result = repository.establishSession()

        assertTrue(result is AuthResult.Success)
        val success = result as AuthResult.Success
        assertEquals("session-token", success.session.token.raw)
        assertEquals("inst-1", success.session.installationId)
        assertEquals(now + 60_000L, success.session.expiresAtEpochMs)

        val persisted = store.saved
        assertEquals("session-token", persisted?.token)
        assertEquals("inst-1", persisted?.installationId)

        val expectedSignature = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(ByteArray(64) { 0x01 })
        assertEquals(expectedSignature, remote.verifyRequest?.signature_b64)
        assertEquals("ch-1", remote.verifyRequest?.challenge_id)
        assertEquals("inst-1", remote.verifyRequest?.installation_id)
    }

    @Test
    fun `fails with CHALLENGE_EXPIRED when server issued an already-expired challenge`() = runTest {
        val repository = DefaultAuthRepository(
            identityRepository = FakeIdentityRepository(),
            keyStore = FakeKeyStore(),
            remoteClient = FakeRemoteClient(
                challengeResult = {
                    ApiResult.Success(ChallengeResponse("ch-1", nonceB64, now - 1L))
                },
            ),
            sessionStore = FakeSessionStore(),
            clock = { now },
        )

        val result = repository.establishSession()

        assertTrue(result is AuthResult.Failure)
        assertEquals(AuthError.CHALLENGE_EXPIRED, (result as AuthResult.Failure).error)
    }

    @Test
    fun `fails with MALFORMED_CHALLENGE when nonce cannot be decoded`() = runTest {
        val repository = DefaultAuthRepository(
            identityRepository = FakeIdentityRepository(),
            keyStore = FakeKeyStore(),
            remoteClient = FakeRemoteClient(
                challengeResult = {
                    ApiResult.Success(ChallengeResponse("ch-1", "###not-base64url###", now + 60_000L))
                },
            ),
            sessionStore = FakeSessionStore(),
            clock = { now },
        )

        val result = repository.establishSession()

        assertTrue(result is AuthResult.Failure)
        assertEquals(AuthError.MALFORMED_CHALLENGE, (result as AuthResult.Failure).error)
    }

    @Test
    fun `fails with UNKNOWN_INSTALLATION when server does not know us`() = runTest {
        val repository = DefaultAuthRepository(
            identityRepository = FakeIdentityRepository(),
            keyStore = FakeKeyStore(),
            remoteClient = FakeRemoteClient(
                challengeResult = { ApiResult.ApiError("UNKNOWN_INSTALLATION", "nope", 404) },
            ),
            sessionStore = FakeSessionStore(),
            clock = { now },
        )

        val result = repository.establishSession()

        assertTrue(result is AuthResult.Failure)
        assertEquals(AuthError.UNKNOWN_INSTALLATION, (result as AuthResult.Failure).error)
    }

    @Test
    fun `fails with INVALID_SIGNATURE when server rejects proof-of-possession`() = runTest {
        val repository = DefaultAuthRepository(
            identityRepository = FakeIdentityRepository(),
            keyStore = FakeKeyStore(),
            remoteClient = FakeRemoteClient(
                challengeResult = {
                    ApiResult.Success(ChallengeResponse("ch-1", nonceB64, now + 60_000L))
                },
                verifyResult = { ApiResult.ApiError("INVALID_SIGNATURE", "bad", 401) },
            ),
            sessionStore = FakeSessionStore(),
            clock = { now },
        )

        val result = repository.establishSession()

        assertTrue(result is AuthResult.Failure)
        assertEquals(AuthError.INVALID_SIGNATURE, (result as AuthResult.Failure).error)
    }

    @Test
    fun `fails with NETWORK when transport is unavailable`() = runTest {
        val repository = DefaultAuthRepository(
            identityRepository = FakeIdentityRepository(),
            keyStore = FakeKeyStore(),
            remoteClient = FakeRemoteClient(
                registerResult = {
                    ApiResult.NetworkError(
                        com.vexcompany.samvira.data.network.FailureReason.CONNECTIVITY,
                    )
                },
            ),
            sessionStore = FakeSessionStore(),
            clock = { now },
        )

        val result = repository.establishSession()

        assertTrue(result is AuthResult.Failure)
        assertEquals(AuthError.NETWORK, (result as AuthResult.Failure).error)
    }

    @Test
    fun `currentSession returns None when nothing is persisted`() = runTest {
        val repository = DefaultAuthRepository(
            identityRepository = FakeIdentityRepository(),
            keyStore = FakeKeyStore(),
            remoteClient = FakeRemoteClient(),
            sessionStore = FakeSessionStore(),
            clock = { now },
        )

        assertEquals(SessionState.None, repository.currentSession())
    }

    @Test
    fun `currentSession clears an expired session and returns None`() = runTest {
        val store = FakeSessionStore(
            initial = SessionRecord("tok", "inst-1", now - 1L),
        )
        val repository = DefaultAuthRepository(
            identityRepository = FakeIdentityRepository(),
            keyStore = FakeKeyStore(),
            remoteClient = FakeRemoteClient(),
            sessionStore = store,
            clock = { now },
        )

        assertEquals(SessionState.None, repository.currentSession())
        assertNull(store.saved)
    }

    @Test
    fun `signOut revokes on the server and clears local state`() = runTest {
        val store = FakeSessionStore(
            initial = SessionRecord("tok", "inst-1", future),
        )
        val remote = FakeRemoteClient()
        val repository = DefaultAuthRepository(
            identityRepository = FakeIdentityRepository(),
            keyStore = FakeKeyStore(),
            remoteClient = remote,
            sessionStore = store,
            clock = { now },
        )

        repository.signOut()

        assertEquals("tok", remote.revokedToken)
        assertNull(store.saved)
    }

    // --- fakes ---

    private class FakeIdentityRepository : InstallationIdentityRepository {
        override suspend fun currentIdentity() = InstallationIdentity(
            installationId = "inst-1",
            publicKeyPem = "-----BEGIN PUBLIC KEY-----\nfake\n-----END PUBLIC KEY-----",
            createdAtEpochMs = 0L,
        )
    }

    private class FakeKeyStore : InstallKeyStore {
        override fun ensureKey(): String = "-----BEGIN PUBLIC KEY-----\nfake\n-----END PUBLIC KEY-----"
        override fun publicKey(): String? = ensureKey()
        override fun sign(data: ByteArray): ByteArray = ByteArray(64) { 0x01 }
    }

    private class FakeRemoteClient(
        private val registerResult: (suspend () -> ApiResult<RegisterResponse>)? = null,
        private val challengeResult: (suspend () -> ApiResult<ChallengeResponse>)? = null,
        private val verifyResult: (suspend () -> ApiResult<VerifyResponse>)? = null,
    ) : RemoteClient {
        var revokedToken: String? = null
        var verifyRequest: VerifyRequest? = null

        override suspend fun register(request: RegisterRequest): ApiResult<RegisterResponse> =
            registerResult?.invoke()
                ?: ApiResult.Success(RegisterResponse(request.installation_id, 0L, false))

        override suspend fun requestChallenge(installationId: String): ApiResult<ChallengeResponse> =
            challengeResult?.invoke()
                ?: ApiResult.ApiError("UNKNOWN_INSTALLATION", "unconfigured", 404)

        override suspend fun verify(request: VerifyRequest): ApiResult<VerifyResponse> {
            verifyRequest = request
            return verifyResult?.invoke()
                ?: ApiResult.Success(VerifyResponse(request.installation_id, "tok", 0L))
        }

        override suspend fun revokeSession(sessionToken: String): ApiResult<com.vexcompany.samvira.data.remote.RevokeSessionResponse> {
            revokedToken = sessionToken
            return ApiResult.Success(com.vexcompany.samvira.data.remote.RevokeSessionResponse(revoked = true))
        }
    }

    private class FakeSessionStore(
        initial: SessionRecord? = null,
    ) : SessionStore {
        var saved: SessionRecord? = initial

        override suspend fun load(): SessionRecord? = saved

        override suspend fun save(record: SessionRecord) {
            saved = record
        }

        override suspend fun clear() {
            saved = null
        }
    }
}
