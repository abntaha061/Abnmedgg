package com.example.ytdownloader.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ytdl_settings")

class UserPreferences(private val context: Context) {

    companion object {
        private val DOWNLOAD_URI_KEY = stringPreferencesKey("download_uri")
        private val INCOGNITO_MODE_KEY = booleanPreferencesKey("incognito_mode")
        private val DEFAULT_QUALITY_KEY = stringPreferencesKey("default_quality")
        private val COBALT_SERVER_KEY = stringPreferencesKey("cobalt_server")
    }

    val downloadUri: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[DOWNLOAD_URI_KEY] }

    val isIncognitoMode: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[INCOGNITO_MODE_KEY] ?: false }

    val defaultQuality: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[DEFAULT_QUALITY_KEY] ?: "1080p" }

    val cobaltServerUrl: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[COBALT_SERVER_KEY] ?: "https://cobalt.inst.moe" }

    suspend fun setDownloadUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri != null) {
                preferences[DOWNLOAD_URI_KEY] = uri
            } else {
                preferences.remove(DOWNLOAD_URI_KEY)
            }
        }
    }

    suspend fun setIncognitoMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[INCOGNITO_MODE_KEY] = enabled
        }
    }

    suspend fun setDefaultQuality(quality: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_QUALITY_KEY] = quality
        }
    }

    suspend fun setCobaltServerUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[COBALT_SERVER_KEY] = url
        }
    }
}
