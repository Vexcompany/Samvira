package com.vexcompany.samvira.data.auth

/**
 * Persisted bootstrap/session state.
 *
 * Only non-secret session metadata is modeled here; the token itself is a
 * short-lived bearer credential stored in private app storage. The server can
 * revoke it at any time, and the app treats rejection as authoritative.
 */
data class SessionRecord(
    val token: String,
    val installationId: String,
    val expiresAtEpochMs: Long,
)

/**
 * Abstraction over local persistence of the session record. The concrete
 * implementation is DataStore-backed and can evolve independently.
 */
interface SessionStore {
    suspend fun load(): SessionRecord?
    suspend fun save(record: SessionRecord)
    suspend fun clear()
}
