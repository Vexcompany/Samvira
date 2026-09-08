package com.vexcompany.samvira.data.identity

/**
 * Persisted, non-secret portion of the installation identity.
 *
 * Key material is deliberately absent from this record; it stays in the
 * Android Keystore and is accessed through the security layer.
 */
data class InstallationIdentityRecord(
    val installationId: String,
    val createdAtEpochMs: Long,
)

/**
 * Abstraction over local persistence of installation identity metadata.
 * The concrete implementation is DataStore-backed today and can evolve
 * without affecting the domain layer.
 */
interface InstallationIdentityStore {
    suspend fun load(): InstallationIdentityRecord?
    suspend fun save(record: InstallationIdentityRecord)
}
