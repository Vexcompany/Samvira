package com.vexcompany.samvira.core.di

import android.content.Context
import com.vexcompany.samvira.BuildConfig
import com.vexcompany.samvira.core.logging.AppLogger
import com.vexcompany.samvira.core.logging.PlatformAppLogger
import com.vexcompany.samvira.data.auth.DataStoreSessionStore
import com.vexcompany.samvira.data.auth.SessionStore
import com.vexcompany.samvira.data.identity.DataStoreInstallationIdentityStore
import com.vexcompany.samvira.data.identity.InstallationIdentityStore
import com.vexcompany.samvira.data.network.NetworkClient
import com.vexcompany.samvira.data.network.OkHttpNetworkClient
import com.vexcompany.samvira.data.remote.HttpRemoteClient
import com.vexcompany.samvira.data.remote.RemoteClient
import com.vexcompany.samvira.domain.auth.AuthRepository
import com.vexcompany.samvira.domain.auth.DefaultAuthRepository
import com.vexcompany.samvira.domain.identity.DefaultInstallationIdentityRepository
import com.vexcompany.samvira.domain.identity.InstallationIdentityRepository
import com.vexcompany.samvira.domain.org.DefaultOrganizationRepository
import com.vexcompany.samvira.domain.org.OrganizationRepository
import com.vexcompany.samvira.domain.org.OrganizationSelectionStore
import com.vexcompany.samvira.security.keystore.AndroidKeystoreInstallKeyStore
import com.vexcompany.samvira.security.keystore.InstallKeyStore
import kotlinx.serialization.json.Json

/**
 * Manual dependency container.
 *
 * Every concrete dependency is declared here as a lazily-constructed property.
 * Boundaries between UI / domain / data / security layers are preserved by
 * exposing interfaces ([InstallationIdentityRepository], [AuthRepository],
 * [RemoteClient], [NetworkClient], [InstallKeyStore], [InstallationIdentityStore],
 * [SessionStore]) rather than their platform-specific implementations.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Redaction-aware logging boundary. */
    val logger: AppLogger = PlatformAppLogger()

    /**
     * JSON codec for the versioned contract. Unknown keys are ignored (safe
     * evolution of server responses) and malformed input fails parsing rather
     * than silently coercing.
     */
    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    /** Security: non-exportable installation key, backed by Android Keystore. */
    val installKeyStore: InstallKeyStore by lazy {
        AndroidKeystoreInstallKeyStore()
    }

    /** Data: local persistence for installation identity metadata. */
    val identityStore: InstallationIdentityStore by lazy {
        DataStoreInstallationIdentityStore(appContext)
    }

    /** Data: local persistence for the bootstrap session. */
    val sessionStore: SessionStore by lazy {
        DataStoreSessionStore(appContext)
    }

    /** Data: networking abstraction. */
    val networkClient: NetworkClient by lazy {
        OkHttpNetworkClient(logger = logger)
    }

    /** Data: backend contract client over the network abstraction. */
    val remoteClient: RemoteClient by lazy {
        HttpRemoteClient(
            baseUrl = BuildConfig.API_BASE_URL,
            networkClient = networkClient,
            json = json,
            logger = logger,
        )
    }

    /** Domain: installation identity orchestration. */
    val identityRepository: InstallationIdentityRepository by lazy {
        DefaultInstallationIdentityRepository(
            keyStore = installKeyStore,
            identityStore = identityStore,
        )
    }

    /** Domain: registration / proof-of-possession / session orchestration. */
    val authRepository: AuthRepository by lazy {
        DefaultAuthRepository(
            identityRepository = identityRepository,
            keyStore = installKeyStore,
            remoteClient = remoteClient,
            sessionStore = sessionStore,
        )
    }

    /** Domain: organization membership (server-authoritative). */
    val organizationRepository: OrganizationRepository by lazy {
        DefaultOrganizationRepository(
            sessionStore = sessionStore,
            remoteClient = remoteClient,
        )
    }

    /** App state: the currently selected organization (process-scoped). */
    val organizationSelection: OrganizationSelectionStore = OrganizationSelectionStore()
}
