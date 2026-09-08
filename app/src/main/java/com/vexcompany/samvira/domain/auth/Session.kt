package com.vexcompany.samvira.domain.auth

/**
 * A session bearer credential.
 *
 * [toString] is overridden to redact the token, so accidental interpolation
 * into logs or UI never exposes the raw credential.
 */
@JvmInline
value class SessionToken(val raw: String) {
    override fun toString(): String = "<redacted>"
}

/**
 * Bootstrap/session state established after proof-of-possession succeeds.
 *
 * The server is authoritative: a session is only "valid" for as long as the
 * server accepts it. [expiresAtEpochMs] is a client-side mirror of the
 * server-issued expiry, used to avoid pointless requests.
 */
data class Session(
    val token: SessionToken,
    val installationId: String,
    val expiresAtEpochMs: Long,
)

sealed interface SessionState {
    data object None : SessionState
    data class Active(val session: Session) : SessionState
}
