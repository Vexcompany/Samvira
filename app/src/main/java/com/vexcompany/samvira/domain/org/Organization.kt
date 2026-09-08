package com.vexcompany.samvira.domain.org

/**
 * Membership state as reported by the server.
 *
 * The server is authoritative: the client never infers membership or grants
 * itself access. [UNKNOWN] exists so an unrecognized state fails closed
 * (treated as not authorized) instead of being silently accepted.
 */
enum class MembershipState {
    ACTIVE,
    PENDING,
    REVOKED,
    SUSPENDED,
    UNKNOWN,
    ;

    val isActive: Boolean get() = this == ACTIVE

    companion object {
        fun fromWire(value: String): MembershipState = when (value) {
            "ACTIVE" -> ACTIVE
            "PENDING" -> PENDING
            "REVOKED" -> REVOKED
            "SUSPENDED" -> SUSPENDED
            else -> UNKNOWN
        }
    }
}

/** An organization this installation belongs to, as reported by the server. */
data class Organization(
    val id: String,
    val name: String,
    val state: MembershipState,
)

/**
 * The currently selected organization, held in app state.
 *
 * Selection only becomes [Selected] after the server validates the
 * organization context; before that it is [None]. This prevents presenting
 * data from a stale or unauthorized organization.
 */
sealed interface OrganizationSelection {
    data object None : OrganizationSelection
    data class Selected(val organization: Organization) : OrganizationSelection
}
