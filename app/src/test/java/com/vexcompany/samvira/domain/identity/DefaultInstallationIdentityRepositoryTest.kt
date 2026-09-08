package com.vexcompany.samvira.domain.identity

import com.vexcompany.samvira.data.identity.InstallationIdentityRecord
import com.vexcompany.samvira.data.identity.InstallationIdentityStore
import com.vexcompany.samvira.security.keystore.InstallKeyStore
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
            record = InstallationIdentityRecord("existing-id", 99L)
        }
        val repository = DefaultInstallationIdentityRepository(keyStore, store)

        val identity = repository.currentIdentity()

        assertEquals("existing-id", identity.installationId)
        assertEquals(99L, identity.createdAtEpochMs)
        assertEquals(FakeInstallKeyStore.DEFAULT_PEM, identity.publicKeyPem)
        assertEquals(0, keyStore.ensureKeyCalls)
    }

    @Test
    fun `re-provisions when metadata exists but key is missing`() = runTest {
        val keyStore = FakeInstallKeyStore(existingPem = null)
        val store = InMemoryIdentityStore().apply {
            record = InstallationIdentityRecord("stale-id", 5L)
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
        assertEquals(1, keyStore.ensureKeyCalls)
    }

    @Test
    fun `concurrent initialization produces a single identity`() = runTest {
        val keyStore = FakeInstallKeyStore(existingPem = null)
        val store = InMemoryIdentityStore(yieldOnAccess = true)
        val idCounter = AtomicInteger(0)
        val repository = DefaultInstallationIdentityRepository(
            keyStore = keyStore,
            identityStore = store,
            idGenerator = { "id-${idCounter.incrementAndGet()}" },
            clock = { 42L },
        )

        val identities = (1..32).map { async { repository.currentIdentity() } }.awaitAll()

        assertEquals(1, keyStore.ensureKeyCalls)
        assertEquals(1, store.saveCalls)
        assertTrue(identities.all { it.installationId == "id-1" })
        assertTrue(identities.all { it.publicKeyPem == FakeInstallKeyStore.DEFAULT_PEM })
    }

    @Test
    fun `fails closed when key provisioning throws`() = runTest {
        val keyStore = object : InstallKeyStore {
            override fun ensureKey(): String = throw IllegalStateException("keystore unavailable")
            override fun publicKey(): String? = null
            override fun sign(data: ByteArray): ByteArray = throw UnsupportedOperationException()
        }
        val store = InMemoryIdentityStore()
        val repository = DefaultInstallationIdentityRepository(
            keyStore = keyStore,
            identityStore = store,
        )

        val thrown = runCatching { repository.currentIdentity() }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
        assertNull(store.record)
        assertEquals(0, store.saveCalls)
    }

    @Test
    fun `fails closed when persistence save throws`() = runTest {
        val keyStore = FakeInstallKeyStore(existingPem = null)
        val store = object : InstallationIdentityStore {
            override suspend fun load(): InstallationIdentityRecord? = null
            override suspend fun save(record: InstallationIdentityRecord) {
                throw IOException("disk unavailable")
            }
        }
        val repository = DefaultInstallationIdentityRepository(keyStore, store)

        val thrown = runCatching { repository.currentIdentity() }.exceptionOrNull()

        assertTrue(thrown is IOException)
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

    private class InMemoryIdentityStore(
        private val yieldOnAccess: Boolean = false,
    ) : InstallationIdentityStore {
        var record: InstallationIdentityRecord? = null
        var saveCalls = 0
            private set

        override suspend fun load(): InstallationIdentityRecord? {
            if (yieldOnAccess) yield()
            return record
        }

        override suspend fun save(record: InstallationIdentityRecord) {
            if (yieldOnAccess) yield()
            saveCalls++
            this.record = record
        }
    }
}
