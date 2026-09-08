package com.vexcompany.samvira.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "session",
)

/**
 * DataStore-backed [SessionStore].
 *
 * The store is private app storage and backups are disabled app-wide, so the
 * token is not exported. (A future milestone may additionally encrypt the
 * token at rest with a Keystore-backed key.)
 */
class DataStoreSessionStore(context: Context) : SessionStore {

    private val dataStore = context.applicationContext.sessionDataStore

    override suspend fun load(): SessionRecord? {
        val prefs = dataStore.data.first()
        val token = prefs[KEY_TOKEN] ?: return null
        val installationId = prefs[KEY_INSTALLATION_ID] ?: return null
        val expiresAt = prefs[KEY_EXPIRES_AT] ?: return null
        return SessionRecord(
            token = token,
            installationId = installationId,
            expiresAtEpochMs = expiresAt,
        )
    }

    override suspend fun save(record: SessionRecord) {
        dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = record.token
            prefs[KEY_INSTALLATION_ID] = record.installationId
            prefs[KEY_EXPIRES_AT] = record.expiresAtEpochMs
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_TOKEN = stringPreferencesKey("session_token")
        val KEY_INSTALLATION_ID = stringPreferencesKey("installation_id")
        val KEY_EXPIRES_AT = longPreferencesKey("session_expires_at_epoch_ms")
    }
}
