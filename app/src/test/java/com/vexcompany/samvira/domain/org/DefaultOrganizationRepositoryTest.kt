package com.vexcompany.samvira.domain.org

import com.vexcompany.samvira.data.auth.SessionRecord
import com.vexcompany.samvira.data.auth.SessionStore
import com.vexcompany.samvira.data.remote.ApiResult
import com.vexcompany.samvira.data.remote.ChallengeResponse
import com.vexcompany.samvira.data.remote.OrganizationContextResponse
import com.vexcompany.samvira.data.remote.OrganizationsResponse
import com.vexcompany.samvira.data.remote.OrganizationSummaryDto
import com.vexcompany.samvira.data.remote.RegisterRequest
import com.vexcompany.samvira.data.remote.RegisterResponse
import com.vexcompany.samvira.data.remote.RemoteClient
import com.vexcompany.samvira.data.remote.RevokeSessionResponse
import com.vexcompany.samvira.data.remote.VerifyRequest
import com.vexcompany.samvira.data.remote.VerifyResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultOrganizationRepositoryTest {

    private val now = 1_700_000_000_000L
    private val future = now + 60_000L

    @Test
    fun `lists organizations mapped from the server`() = runTest {
        val remote = FakeRemoteClient(
            organizationsResult = {
                ApiResult.Success(
                    OrganizationsResponse(
                        organizations = listOf(
                            OrganizationSummaryDto("org-1", "One", "ACTIVE"),
                            OrganizationSummaryDto("org-2", "Two", "PENDING"),
                        ),
                    ),
                )
            },
        )
        val repository = DefaultOrganizationRepository(
            sessionStore = FakeSessionStore(SessionRecord("tok", "inst-1", future)),
            remoteClient = remote,
            clock = { now },
        )

        val result = repository.listOrganizations()

        assertTrue(result is OrganizationsResult.Success)
        val orgs = (result as OrganizationsResult.Success).organizations
        assertEquals(2, orgs.size)
        assertEquals(MembershipState.ACTIVE, orgs[0].state)
        assertEquals(MembershipState.PENDING, orgs[1].state)
        assertEquals("tok", remote.lastListToken)
    }

    @Test
    fun `returns NO_SESSION when nothing is persisted`() = runTest {
        val repository = DefaultOrganizationRepository(
            sessionStore = FakeSessionStore(null),
            remoteClient = FakeRemoteClient(),
            clock = { now },
        )

        val result = repository.listOrganizations()

        assertTrue(result is OrganizationsResult.Failure)
        assertEquals(OrganizationError.NO_SESSION, (result as OrganizationsResult.Failure).error)
    }

    @Test
    fun `returns NO_SESSION when the persisted session is expired`() = runTest {
        val repository = DefaultOrganizationRepository(
            sessionStore = FakeSessionStore(SessionRecord("tok", "inst-1", now - 1L)),
            remoteClient = FakeRemoteClient(),
            clock = { now },
        )

        val result = repository.listOrganizations()

        assertTrue(result is OrganizationsResult.Failure)
        assertEquals(OrganizationError.NO_SESSION, (result as OrganizationsResult.Failure).error)
    }

    @Test
    fun `maps rejected session to SESSION_REJECTED`() = runTest {
        val repository = DefaultOrganizationRepository(
            sessionStore = FakeSessionStore(SessionRecord("tok", "inst-1", future)),
            remoteClient = FakeRemoteClient(
                organizationsResult = { ApiResult.ApiError("SESSION_REVOKED", "revoked", 401) },
            ),
            clock = { now },
        )

        val result = repository.listOrganizations()

        assertTrue(result is OrganizationsResult.Failure)
        assertEquals(OrganizationError.SESSION_REJECTED, (result as OrganizationsResult.Failure).error)
    }

    @Test
    fun `maps membership rejection for context to MEMBERSHIP_NOT_ACTIVE`() = runTest {
        val repository = DefaultOrganizationRepository(
            sessionStore = FakeSessionStore(SessionRecord("tok", "inst-1", future)),
            remoteClient = FakeRemoteClient(
                contextResult = { ApiResult.ApiError("MEMBERSHIP_NOT_ACTIVE", "suspended", 403) },
            ),
            clock = { now },
        )

        val result = repository.organizationContext("org-1")

        assertTrue(result is OrganizationContextResult.Failure)
        assertEquals(OrganizationError.MEMBERSHIP_NOT_ACTIVE, (result as OrganizationContextResult.Failure).error)
    }

    @Test
    fun `fetches organization context with the requested id`() = runTest {
        val remote = FakeRemoteClient(
            contextResult = {
                ApiResult.Success(OrganizationContextResponse("org-1", "One", "ACTIVE"))
            },
        )
        val repository = DefaultOrganizationRepository(
            sessionStore = FakeSessionStore(SessionRecord("tok", "inst-1", future)),
            remoteClient = remote,
            clock = { now },
        )

        val result = repository.organizationContext("org-1")

        assertTrue(result is OrganizationContextResult.Success)
        val org = (result as OrganizationContextResult.Success).organization
        assertEquals("org-1", org.id)
        assertEquals(MembershipState.ACTIVE, org.state)
        assertEquals("org-1", remote.lastContextId)
    }

    @Test
    fun `maps an unknown membership state to UNKNOWN and fails closed`() = runTest {
        val remote = FakeRemoteClient(
            contextResult = {
                ApiResult.Success(OrganizationContextResponse("org-1", "One", "WEIRD_STATE"))
            },
        )
        val repository = DefaultOrganizationRepository(
            sessionStore = FakeSessionStore(SessionRecord("tok", "inst-1", future)),
            remoteClient = remote,
            clock = { now },
        )

        val result = repository.organizationContext("org-1")

        assertTrue(result is OrganizationContextResult.Success)
        val org = (result as OrganizationContextResult.Success).organization
        assertEquals(MembershipState.UNKNOWN, org.state)
        assertTrue(!org.state.isActive)
    }

    // --- fakes ---

    private class FakeSessionStore(initial: SessionRecord?) : SessionStore {
        private var saved: SessionRecord? = initial
        override suspend fun load(): SessionRecord? = saved
        override suspend fun save(record: SessionRecord) {
            saved = record
        }

        override suspend fun clear() {
            saved = null
        }
    }

    private class FakeRemoteClient(
        private val organizationsResult: (suspend () -> ApiResult<OrganizationsResponse>)? = null,
        private val contextResult: (suspend () -> ApiResult<OrganizationContextResponse>)? = null,
    ) : RemoteClient {
        var lastListToken: String? = null
        var lastContextId: String? = null

        override suspend fun register(request: RegisterRequest): ApiResult<RegisterResponse> =
            ApiResult.ApiError("UNCONFIGURED", "unused", 500)

        override suspend fun requestChallenge(installationId: String): ApiResult<ChallengeResponse> =
            ApiResult.ApiError("UNCONFIGURED", "unused", 500)

        override suspend fun verify(request: VerifyRequest): ApiResult<VerifyResponse> =
            ApiResult.ApiError("UNCONFIGURED", "unused", 500)

        override suspend fun revokeSession(sessionToken: String): ApiResult<RevokeSessionResponse> =
            ApiResult.Success(RevokeSessionResponse(true))

        override suspend fun listOrganizations(sessionToken: String): ApiResult<OrganizationsResponse> {
            lastListToken = sessionToken
            return organizationsResult?.invoke()
                ?: ApiResult.ApiError("UNCONFIGURED", "unused", 500)
        }

        override suspend fun organizationContext(
            sessionToken: String,
            organizationId: String,
        ): ApiResult<OrganizationContextResponse> {
            lastContextId = organizationId
            return contextResult?.invoke()
                ?: ApiResult.ApiError("UNCONFIGURED", "unused", 500)
        }
    }
}
