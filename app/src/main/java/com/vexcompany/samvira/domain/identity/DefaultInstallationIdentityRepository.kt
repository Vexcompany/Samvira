package com.vexcompany.samvira.domain.identity

import com.vexcompany.samvira.data.identity.InstallationIdentityRecord
import com.vexcompany.samvira.data.identity.InstallationIdentityStore
import com.vexcompany.samvira.security.keystore.InstallKeyStore
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Orchestrates installation identity between Android Keystore key material and
 * local persistence of the random identifier and creation time.
 */
class DefaultInstallationIdentityRepository(
    private val keyStore: InstallKeyStore,
    private val identityStore: InstallationIdentityStore,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
) : InstallationIdentityRepository {

    // Serializes first-use provisioning so concurrent callers cannot mint
    // multiple identifiers or race key creation for the same installation.
    private val provisioningMutex = Mutex()

    override suspend fun currentIdentity(): InstallationIdentity =
        withContext(Dispatchers.IO) {
            provisioningMutex.withLock {
                val stored = identityStore.load()
                val publicKey = keyStore.publicKey()

                if (stored != null && publicKey != null) {
                    return@withLock stored.toIdentity(publicKey)
                }

                // First launch, or key/metadata became inconsistent. Reuse an
                // existing key when possible and create fresh metadata. If either
                // provisioning or persistence fails, the exception propagates and
                // no partial identity is returned to the caller.
                val pem = keyStore.ensureKey()
                val record = InstallationIdentityRecord(
                    installationId = idGenerator(),
                    createdAtEpochMs = clock(),
                )
                identityStore.save(record)
                record.toIdentity(pem)
            }
        }
}

private fun InstallationIdentityRecord.toIdentity(publicKeyPem: String) = InstallationIdentity(
    installationId = installationId,
    publicKeyPem = publicKeyPem,
    createdAtEpochMs = createdAtEpochMs,
)
