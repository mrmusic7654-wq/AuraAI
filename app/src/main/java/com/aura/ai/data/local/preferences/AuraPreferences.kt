package com.aura.ai.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.aura.ai.utils.constants.AppConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuraPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    
    private val encryptedPreferences = EncryptedSharedPreferences.create(
        "aura_secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    companion object {
        private val API_KEY = stringPreferencesKey("api_key")
        private val USER_NAME = stringPreferencesKey("user_name")
        private val SELECTED_MODEL = stringPreferencesKey("selected_model")
        private val GITHUB_TOKEN = stringPreferencesKey("github_token")
    }
    
    // API Key Management (Encrypted)
    fun saveApiKey(apiKey: String) {
        encryptedPreferences.edit().putString(AppConstants.KEY_API_KEY, apiKey).apply()
    }
    
    fun getApiKey(): String? {
        return encryptedPreferences.getString(AppConstants.KEY_API_KEY, null)
    }
    
    suspend fun saveApiKeyToDataStore(apiKey: String) {
        dataStore.edit { preferences ->
            preferences[API_KEY] = apiKey
        }
    }
    
    fun getApiKeyFlow(): Flow<String?> {
        return dataStore.data.map { preferences ->
            preferences[API_KEY]
        }
    }
    
    suspend fun getApiKeyFromDataStore(): String? {
        return dataStore.data.map { it[API_KEY] }.first()
    }
    
    // GitHub Token (Encrypted)
    fun saveGitHubToken(token: String) {
        encryptedPreferences.edit().putString(AppConstants.KEY_GITHUB_TOKEN, token).apply()
    }
    
    fun getGitHubToken(): String? {
        return encryptedPreferences.getString(AppConstants.KEY_GITHUB_TOKEN, null)
    }
    
    // User Preferences
    suspend fun saveUserName(name: String) {
        dataStore.edit { preferences ->
            preferences[USER_NAME] = name
        }
    }
    
    fun getUserNameFlow(): Flow<String?> {
        return dataStore.data.map { preferences ->
            preferences[USER_NAME]
        }
    }
    
    suspend fun saveSelectedModel(model: String) {
        dataStore.edit { preferences ->
            preferences[SELECTED_MODEL] = model
        }
    }
    
    fun getSelectedModelFlow(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[SELECTED_MODEL] ?: AppConstants.DEFAULT_MODEL
        }
    }
    
    // Clear all data
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
        encryptedPreferences.edit().clear().apply()
    }
}
