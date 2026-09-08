package com.vexcompany.samvira.data.identity

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.identityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "installation_identity",
)

/**
 * DataStore-backed [InstallationIdentityStore].
 *
 * DataStore is preferred over raw SharedPreferences here because it exposes a
 * Flow, is transaction-safe, and is the modern Android persistence primitive
 * for small key/value state.
 */
class DataStoreInstallationIdentityStore(context: Context) : InstallationIdentityStore {

    private val dataStore = context.applicationContext.identityDataStore

    override suspend fun load(): InstallationIdentityRecord? {
        val prefs = dataStore.data.first()
        val installationId = prefs[KEY_INSTALLATION_ID] ?: return null
        val createdAt = prefs[KEY_CREATED_AT] ?: return null
        return InstallationIdentityRecord(
            installationId = installationId,
            createdAtEpochMs = createdAt,
        )
    }

    override suspend fun save(record: InstallationIdentityRecord) {
        dataStore.edit { prefs ->
            prefs[KEY_INSTALLATION_ID] = record.installationId
            prefs[KEY_CREATED_AT] = record.createdAtEpochMs
        }
    }

    private companion object {
        val KEY_INSTALLATION_ID = stringPreferencesKey("installation_id")
        val KEY_CREATED_AT = longPreferencesKey("created_at_epoch_ms")
    }
}
