package com.aura.ai.domain.usecases.settings

import com.aura.ai.data.local.preferences.AuraPreferences
import javax.inject.Inject

class UpdateUserPreferencesUseCase @Inject constructor(
    private val preferences: AuraPreferences
) {
    
    suspend fun updateModel(model: String) {
        preferences.saveSelectedModel(model)
    }
    
    suspend fun updateUserName(name: String) {
        preferences.saveUserName(name)
    }
    
    suspend fun saveGitHubToken(token: String) {
        preferences.saveGitHubToken(token)
    }
}
