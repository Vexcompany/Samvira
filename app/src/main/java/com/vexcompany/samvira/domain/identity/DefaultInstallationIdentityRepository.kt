package com.vexcompany.samvira.domain.identity

import com.vexcompany.samvira.data.identity.InstallationIdentityRecord
import com.vexcompany.samvira.data.identity.InstallationIdentityStore
import com.vexcompany.samvira.security.keystore.InstallKeyStore
import java.util.UUID

/**
 * Orchestrates installation identity between the Android Keystore (key
 * material) and local persistence (random identifier + creation time).
 *
 * Public-key material is always read back from the Keystore; the store only
 * persists the non-secret identifier and timestamp, so the two layers never
 * need to agree on secret data.
 */
class DefaultInstallationIdentityRepository(
    private val keyStore: InstallKeyStore,
    private val identityStore: InstallationIdentityStore,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
) : InstallationIdentityRepository {

    override suspend fun currentIdentity(): InstallationIdentity {
        val stored = identityStore.load()
        val publicKey = keyStore.publicKey()

        if (stored != null && publicKey != null) {
            return stored.toIdentity(publicKey)
        }

        // First launch, or the key/metadata pair became inconsistent
        // (e.g. app data was cleared but the Keystore entry survived).
        // Provision (or reuse) the key and persist a fresh identifier.
        val pem = keyStore.ensureKey()
        val record = InstallationIdentityRecord(
            installationId = idGenerator(),
            createdAtEpochMs = clock(),
        )
        identityStore.save(record)
        return record.toIdentity(pem)
    }
}

private fun InstallationIdentityRecord.toIdentity(publicKeyPem: String) = InstallationIdentity(
    installationId = installationId,
    publicKeyPem = publicKeyPem,
    createdAtEpochMs = createdAtEpochMs,
)
