package com.aura.ai.domain.usecases.settings

import com.aura.ai.data.local.preferences.AuraPreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetApiKeyUseCase @Inject constructor(
    private val preferences: AuraPreferences
) {
    
    operator fun invoke(): String? {
        return preferences.getApiKey()
    }
    
    fun asFlow(): Flow<String?> {
        return preferences.getApiKeyFlow()
    }
    
    suspend fun fromDataStore(): String? {
        return preferences.getApiKeyFromDataStore()
    }
}
