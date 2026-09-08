package com.vexcompany.samvira.domain.auth

/**
 * Domain-level access to authentication/session state.
 *
 * The server remains the source of truth for authorization. This repository
 * performs installation registration and proof-of-possession, then persists
 * the resulting bootstrap session locally. The private key is only ever used
 * through [com.vexcompany.samvira.security.keystore.InstallKeyStore].
 */
interface AuthRepository {

    /**
     * Registers the installation (if needed), obtains a challenge, signs it
     * with the Keystore key, and verifies it to obtain a session.
     *
     * Fails closed: any error at any step returns [AuthResult.Failure] and no
     * partial session is persisted.
     */
    suspend fun establishSession(): AuthResult

    /** Returns the locally persisted session state (no network). */
    suspend fun currentSession(): SessionState

    /** Best-effort server revocation followed by clearing local session state. */
    suspend fun signOut()
}
