package com.vexcompany.samvira.core.di

import android.content.Context
import com.vexcompany.samvira.core.logging.AppLogger
import com.vexcompany.samvira.core.logging.PlatformAppLogger
import com.vexcompany.samvira.data.identity.DataStoreInstallationIdentityStore
import com.vexcompany.samvira.data.identity.InstallationIdentityStore
import com.vexcompany.samvira.data.network.NetworkClient
import com.vexcompany.samvira.data.network.OkHttpNetworkClient
import com.vexcompany.samvira.domain.identity.DefaultInstallationIdentityRepository
import com.vexcompany.samvira.domain.identity.InstallationIdentityRepository
import com.vexcompany.samvira.security.keystore.AndroidKeystoreInstallKeyStore
import com.vexcompany.samvira.security.keystore.InstallKeyStore

/**
 * Manual dependency container.
 *
 * Every concrete dependency is declared here as a lazily-constructed property.
 * Boundaries between UI / domain / data / security layers are preserved by
 * exposing interfaces ([InstallationIdentityRepository], [NetworkClient],
 * [InstallKeyStore], [InstallationIdentityStore]) rather than their
 * platform-specific implementations.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Redaction-aware logging boundary. */
    val logger: AppLogger = PlatformAppLogger()

    /** Security: non-exportable installation key, backed by Android Keystore. */
    val installKeyStore: InstallKeyStore by lazy {
        AndroidKeystoreInstallKeyStore()
    }

    /** Data: local persistence for installation identity metadata. */
    val identityStore: InstallationIdentityStore by lazy {
        DataStoreInstallationIdentityStore(appContext)
    }

    /** Data: networking abstraction. */
    val networkClient: NetworkClient by lazy {
        OkHttpNetworkClient(logger = logger)
    }

    /** Domain: installation identity orchestration. */
    val identityRepository: InstallationIdentityRepository by lazy {
        DefaultInstallationIdentityRepository(
            keyStore = installKeyStore,
            identityStore = identityStore,
        )
    }
}
