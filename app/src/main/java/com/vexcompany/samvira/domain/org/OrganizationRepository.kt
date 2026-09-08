package com.vexcompany.samvira.domain.org

/** Coarse-grained organization failure taxonomy. */
enum class OrganizationError {
    NO_SESSION,
    SESSION_REJECTED,
    NOT_A_MEMBER,
    MEMBERSHIP_NOT_ACTIVE,
    UNKNOWN_ORGANIZATION,
    ORG_CONTEXT_INVALID,
    NETWORK,
    MALFORMED_RESPONSE,
    UNKNOWN,
}

sealed interface OrganizationsResult {
    data class Success(val organizations: List<Organization>) : OrganizationsResult
    data class Failure(val error: OrganizationError) : OrganizationsResult
}

sealed interface OrganizationContextResult {
    data class Success(val organization: Organization) : OrganizationContextResult
    data class Failure(val error: OrganizationError) : OrganizationContextResult
}

/**
 * Domain-level access to organization membership.
 *
 * Membership and authorization are server-side only. This repository carries a
 * valid session token and the explicitly-requested organization context, and
 * surfaces the server's response; it never asserts membership on its own.
 */
interface OrganizationRepository {

    /** Lists this installation's organizations. */
    suspend fun listOrganizations(): OrganizationsResult

    /**
     * Fetches the context for [organizationId]. Fails closed when the server
     * rejects the session, the membership, or the stated organization context.
     */
    suspend fun organizationContext(organizationId: String): OrganizationContextResult
}
