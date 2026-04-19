package com.aura.ai.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.ai.domain.usecases.settings.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
        loadSettings()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            getApiKeyUseCase.asFlow().collect { apiKey ->
                _state.update { 
                    it.copy(
                        hasApiKey = !apiKey.isNullOrBlank(),
                        apiKey = apiKey ?: ""
                    )
                }
            }
        }
    }
    
    fun saveApiKey(apiKey: String) {
        saveApiKeyUseCase(apiKey)
        viewModelScope.launch {
            saveApiKeyUseCase.saveToDataStore(apiKey)
        }
    }
    
    fun clearApiKey() {
        saveApiKeyUseCase("")
    }
    
    fun exportData(): kotlinx.coroutines.flow.Flow<ExportResult> = flow {
        emit(ExportResult.Loading)
        
        val result = exportDataUseCase()
        result.fold(
            onSuccess = { file ->
                emit(ExportResult.Success(file))
            },
            onFailure = { error ->
                emit(ExportResult.Error(error.message ?: "Export failed"))
            }
        )
    }
    
    fun updateModel(model: String) {
        viewModelScope.launch {
            updateUserPreferencesUseCase.updateModel(model)
            _state.update { it.copy(selectedModel = model) }
        }
    }
    
    fun showApiKeyDialog() {
        _state.update { it.copy(showApiKeyDialog = true) }
    }
    
    fun hideApiKeyDialog() {
        _state.update { it.copy(showApiKeyDialog = false) }
    }
}

sealed class ExportResult {
    object Loading : ExportResult()
    data class Success(val file: java.io.File) : ExportResult()
    data class Error(val message: String) : ExportResult()
}
