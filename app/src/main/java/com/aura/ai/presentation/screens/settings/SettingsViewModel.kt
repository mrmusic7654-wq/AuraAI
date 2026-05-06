package com.aura.ai.presentation.screens.settings

import androidx.lifecycle.ViewModel
import com.aura.ai.data.local.preferences.AuraPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AuraPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState(apiKey = preferences.getApiKey() ?: ""))
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun updateApiKey(key: String) { _state.value = _state.value.copy(apiKey = key) }
    
    fun saveApiKey() {
        preferences.saveApiKey(_state.value.apiKey)
        _state.value = _state.value.copy(saved = true)
    }
    
    fun clearSaved() { _state.value = _state.value.copy(saved = false) }
}

data class SettingsState(val apiKey: String = "", val saved: Boolean = false)
