package com.aura.ai.domain.usecases.settings

import com.aura.ai.data.local.preferences.AuraPreferences
import javax.inject.Inject

class SaveApiKeyUseCase @Inject constructor(
    private val preferences: AuraPreferences
) {
    
    operator fun invoke(apiKey: String) {
        preferences.saveApiKey(apiKey)
    }
    
    suspend fun saveToDataStore(apiKey: String) {
        preferences.saveApiKeyToDataStore(apiKey)
    }
}
