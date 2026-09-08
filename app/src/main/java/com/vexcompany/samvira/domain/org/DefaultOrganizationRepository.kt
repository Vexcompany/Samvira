package com.vexcompany.samvira.domain.org

import com.vexcompany.samvira.data.auth.SessionStore
import com.vexcompany.samvira.data.remote.ApiResult
import com.vexcompany.samvira.data.remote.OrganizationContextResponse
import com.vexcompany.samvira.data.remote.OrganizationSummaryDto
import com.vexcompany.samvira.data.remote.RemoteClient

/**
 * [OrganizationRepository] over the session store and the contract client.
 *
 * A missing or locally-expired session maps to [OrganizationError.NO_SESSION];
 * everything else is whatever the server decided. The maximum of two
 * organizations per installation is enforced server-side; this repository
 * simply mirrors the list.
 */
class DefaultOrganizationRepository(
    private val sessionStore: SessionStore,
    private val remoteClient: RemoteClient,
    private val clock: () -> Long = System::currentTimeMillis,
) : OrganizationRepository {

    override suspend fun listOrganizations(): OrganizationsResult {
        val token = sessionToken() ?: return OrganizationsResult.Failure(OrganizationError.NO_SESSION)
        return when (val result = remoteClient.listOrganizations(token)) {
            is ApiResult.Success -> OrganizationsResult.Success(
                result.value.organizations.map { it.toDomain() },
            )

            is ApiResult.ApiError -> OrganizationsResult.Failure(mapError(result.code))
            is ApiResult.NetworkError -> OrganizationsResult.Failure(OrganizationError.NETWORK)
        }
    }

    override suspend fun organizationContext(organizationId: String): OrganizationContextResult {
        val token = sessionToken() ?: return OrganizationContextResult.Failure(OrganizationError.NO_SESSION)
        return when (val result = remoteClient.organizationContext(token, organizationId)) {
            is ApiResult.Success -> OrganizationContextResult.Success(result.value.toDomain())
            is ApiResult.ApiError -> OrganizationContextResult.Failure(mapError(result.code))
            is ApiResult.NetworkError -> OrganizationContextResult.Failure(OrganizationError.NETWORK)
        }
    }

    private suspend fun sessionToken(): String? {
        val record = sessionStore.load() ?: return null
        if (record.expiresAtEpochMs <= clock()) return null
        return record.token
    }

    private fun mapError(code: String): OrganizationError = when (code) {
        "SESSION_INVALID", "SESSION_EXPIRED", "SESSION_REVOKED" -> OrganizationError.SESSION_REJECTED
        "NOT_A_MEMBER" -> OrganizationError.NOT_A_MEMBER
        "MEMBERSHIP_NOT_ACTIVE" -> OrganizationError.MEMBERSHIP_NOT_ACTIVE
        "UNKNOWN_ORGANIZATION" -> OrganizationError.UNKNOWN_ORGANIZATION
        "ORG_CONTEXT_INVALID" -> OrganizationError.ORG_CONTEXT_INVALID
        "MALFORMED_RESPONSE" -> OrganizationError.MALFORMED_RESPONSE
        else -> OrganizationError.UNKNOWN
    }

    private fun OrganizationSummaryDto.toDomain() = Organization(
        id = organization_id,
        name = name,
        state = MembershipState.fromWire(state),
    )

    private fun OrganizationContextResponse.toDomain() = Organization(
        id = organization_id,
        name = name,
        state = MembershipState.fromWire(state),
    )
}
