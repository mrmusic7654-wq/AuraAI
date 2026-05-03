package com.aura.ai.presentation.screens.settings

import androidx.lifecycle.ViewModel
import com.aura.ai.domain.usecases.settings.GetApiKeyUseCase
import com.aura.ai.domain.usecases.settings.SaveApiKeyUseCase
import com.aura.ai.domain.usecases.settings.UpdateUserPreferencesUseCase
import com.aura.ai.domain.usecases.settings.ExportDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val saveApiKeyUseCase: SaveApiKeyUseCase,
    private val getApiKeyUseCase: GetApiKeyUseCase,
    private val updateUserPreferencesUseCase: UpdateUserPreferencesUseCase,
    private val exportDataUseCase: ExportDataUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(apiKey = getApiKeyUseCase() ?: "")
    }

    fun saveApiKey(apiKey: String) {
        saveApiKeyUseCase(apiKey)
        _state.value = _state.value.copy(apiKey = apiKey)
    }

    fun clearApiKey() { saveApiKey("") }
    fun showApiKeyDialog() { _state.value = _state.value.copy(showApiKeyDialog = true) }
    fun hideApiKeyDialog() { _state.value = _state.value.copy(showApiKeyDialog = false) }
}

data class SettingsState(
    val hasApiKey: Boolean = false,
    val apiKey: String = "",
    val selectedModel: String = "gemini-2.0-flash-exp",
    val showApiKeyDialog: Boolean = false
)
