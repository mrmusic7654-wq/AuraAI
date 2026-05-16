package com.aura.ai.domain.usecases.settings

import com.aura.ai.data.local.preferences.AuraPreferences
import javax.inject.Inject

class GetApiKeyUseCase @Inject constructor(
    private val preferences: AuraPreferences
) {
    operator fun invoke(): String? = preferences.getApiKey()
}
