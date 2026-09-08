package com.vexcompany.samvira.domain.identity

/**
 * Domain-level access to the installation identity.
 *
 * The server is the source of truth for authorization; this repository only
 * provides the local, installation-scoped identity that the server may later
 * bind to organization membership. No network registration happens here
 * (that belongs to a future milestone).
 */
interface InstallationIdentityRepository {

    /**
     * Returns the current installation identity, provisioning it on first use.
     *
     * Fails closed: if the stored metadata and the Keystore key disagree (for
     * example after a partial data wipe), a fresh identity is provisioned
     * rather than returning an inconsistent one.
     */
    suspend fun currentIdentity(): InstallationIdentity
}
