package com.vexcompany.samvira.security.keystore

/**
 * Abstraction over the installation signing key.
 *
 * The private key is generated inside Android Keystore and is never exported.
 * Only the public key (PEM) and signature operations are exposed, which is all
 * the identity layer needs to register with, and prove possession to, a server
 * in a future milestone.
 *
 * Implementations are expected to fail loudly (throw) when the key cannot be
 * created or used, so callers fail closed rather than continue without an
 * identity.
 */
interface InstallKeyStore {

    /**
     * Ensures a non-exportable signing key exists, creating it if necessary,
     * and returns its PEM-encoded public key.
     */
    fun ensureKey(): String

    /** Returns the PEM public key, or `null` when no key exists yet. */
    fun publicKey(): String?

    /**
     * Signs [data] with the installation key.
     *
     * Reserved for proof-of-possession / challenge-response authentication in
     * a future milestone; not yet used by any product flow.
     */
    fun sign(data: ByteArray): ByteArray
}
