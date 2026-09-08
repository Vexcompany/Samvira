package com.vexcompany.samvira.core.di

import android.content.Context
import com.vexcompany.samvira.BuildConfig
import com.vexcompany.samvira.core.logging.AppLogger
import com.vexcompany.samvira.core.logging.PlatformAppLogger
import com.vexcompany.samvira.data.auth.DataStoreSessionStore
import com.vexcompany.samvira.data.auth.SessionStore
import com.vexcompany.samvira.data.identity.DataStoreInstallationIdentityStore
import com.vexcompany.samvira.data.identity.InstallationIdentityStore
import com.vexcompany.samvira.data.media.RemoteMediaRepository
import com.vexcompany.samvira.data.network.NetworkClient
import com.vexcompany.samvira.data.network.OkHttpNetworkClient
import com.vexcompany.samvira.data.remote.HttpRemoteClient
import com.vexcompany.samvira.data.remote.RemoteClient
import com.vexcompany.samvira.domain.auth.AuthRepository
import com.vexcompany.samvira.domain.auth.DefaultAuthRepository
import com.vexcompany.samvira.domain.identity.DefaultInstallationIdentityRepository
import com.vexcompany.samvira.domain.identity.InstallationIdentityRepository
import com.vexcompany.samvira.domain.media.MediaRepository
import com.vexcompany.samvira.domain.org.DefaultOrganizationRepository
import com.vexcompany.samvira.domain.org.OrganizationRepository
import com.vexcompany.samvira.domain.org.OrganizationSelectionStore
import com.vexcompany.samvira.security.keystore.AndroidKeystoreInstallKeyStore
import com.vexcompany.samvira.security.keystore.InstallKeyStore
import kotlinx.serialization.json.Json

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val logger: AppLogger = PlatformAppLogger()
    private val json: Json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false; encodeDefaults = true }
    val installKeyStore: InstallKeyStore by lazy { AndroidKeystoreInstallKeyStore() }
    val identityStore: InstallationIdentityStore by lazy { DataStoreInstallationIdentityStore(appContext) }
    val sessionStore: SessionStore by lazy { DataStoreSessionStore(appContext) }
    val networkClient: NetworkClient by lazy { OkHttpNetworkClient(logger = logger) }
    val remoteClient: RemoteClient by lazy { HttpRemoteClient(baseUrl = BuildConfig.API_BASE_URL, networkClient = networkClient, json = json, logger = logger) }
    val identityRepository: InstallationIdentityRepository by lazy { DefaultInstallationIdentityRepository(keyStore = installKeyStore, identityStore = identityStore) }
    val authRepository: AuthRepository by lazy { DefaultAuthRepository(identityRepository = identityRepository, keyStore = installKeyStore, remoteClient = remoteClient, sessionStore = sessionStore) }
    val organizationRepository: OrganizationRepository by lazy { DefaultOrganizationRepository(sessionStore = sessionStore, remoteClient = remoteClient) }
    val mediaRepository: MediaRepository by lazy { RemoteMediaRepository(remoteClient) }
    val organizationSelection: OrganizationSelectionStore = OrganizationSelectionStore()
}
