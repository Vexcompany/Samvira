package com.vexcompany.samvira.domain.identity

import com.vexcompany.samvira.data.identity.InstallationIdentityRecord
import com.vexcompany.samvira.data.identity.InstallationIdentityStore
import com.vexcompany.samvira.security.keystore.InstallKeyStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DefaultInstallationIdentityRepositoryTest {

    @Test
    fun `provisions identity on first access`() = runTest {
        val keyStore = FakeInstallKeyStore(existingPem = null)
        val store = InMemoryIdentityStore()
        val repository = DefaultInstallationIdentityRepository(
            keyStore = keyStore,
            identityStore = store,
            idGenerator = { "fixed-installation-id" },
            clock = { 1234L },
        )

        val identity = repository.currentIdentity()

        assertEquals("fixed-installation-id", identity.installationId)
        assertEquals(FakeInstallKeyStore.DEFAULT_PEM, identity.publicKeyPem)
        assertEquals(1234L, identity.createdAtEpochMs)
        assertEquals(1, keyStore.ensureKeyCalls)
        assertEquals("fixed-installation-id", store.record?.installationId)
    }

    @Test
    fun `returns existing identity without regenerating`() = runTest {
        val keyStore = FakeInstallKeyStore(existingPem = FakeInstallKeyStore.DEFAULT_PEM)
        val store = InMemoryIdentityStore().apply {
            record = InstallationIdentityRecord(
                installationId = "existing-id",
                createdAtEpochMs = 99L,
            )
        }
        val repository = DefaultInstallationIdentityRepository(keyStore, store)

        val identity = repository.currentIdentity()

        assertEquals("existing-id", identity.installationId)
        assertEquals(99L, identity.createdAtEpochMs)
        assertEquals(FakeInstallKeyStore.DEFAULT_PEM, identity.publicKeyPem)
        assertEquals(0, keyStore.ensureKeyCalls)
        assertEquals("existing-id", store.record?.installationId)
    }

    @Test
    fun `re-provisions when metadata exists but key is missing`() = runTest {
        val keyStore = FakeInstallKeyStore(existingPem = null)
        val store = InMemoryIdentityStore().apply {
            record = InstallationIdentityRecord(
                installationId = "stale-id",
                createdAtEpochMs = 5L,
            )
        }
        val repository = DefaultInstallationIdentityRepository(
            keyStore = keyStore,
            identityStore = store,
            idGenerator = { "fresh-id" },
            clock = { 500L },
        )

        val identity = repository.currentIdentity()

        assertEquals("fresh-id", identity.installationId)
        assertEquals(500L, identity.createdAtEpochMs)
        assertEquals(1, keyStore.ensureKeyCalls)
        assertEquals("fresh-id", store.record?.installationId)
    }

    @Test
    fun `re-provisions when key exists but metadata is missing`() = runTest {
        val keyStore = FakeInstallKeyStore(existingPem = FakeInstallKeyStore.DEFAULT_PEM)
        val store = InMemoryIdentityStore()
        val repository = DefaultInstallationIdentityRepository(
            keyStore = keyStore,
            identityStore = store,
            idGenerator = { "recovered-id" },
            clock = { 7L },
        )

        val identity = repository.currentIdentity()

        assertEquals("recovered-id", identity.installationId)
        assertNotNull(store.record)
        assertEquals(FakeInstallKeyStore.DEFAULT_PEM, keyStore.publicKey())
        assertEquals(1, keyStore.ensureKeyCalls)
    }

    private class FakeInstallKeyStore(private var existingPem: String?) : InstallKeyStore {

        var ensureKeyCalls = 0
            private set

        override fun ensureKey(): String {
            ensureKeyCalls++
            val pem = existingPem ?: DEFAULT_PEM
            existingPem = pem
            return pem
        }

        override fun publicKey(): String? = existingPem

        override fun sign(data: ByteArray): ByteArray = data

        companion object {
            const val DEFAULT_PEM =
                "-----BEGIN PUBLIC KEY-----\nZmFrZQ==\n-----END PUBLIC KEY-----\n"
        }
    }

    private class InMemoryIdentityStore : InstallationIdentityStore {
        var record: InstallationIdentityRecord? = null

        override suspend fun load(): InstallationIdentityRecord? = record

        override suspend fun save(record: InstallationIdentityRecord) {
            this.record = record
        }
    }
}
