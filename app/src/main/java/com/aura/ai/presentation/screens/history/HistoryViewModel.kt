package com.aura.ai.presentation.screens.history

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    fun selectTask(task: TaskData) { _state.value = _state.value.copy(selectedTask = task) }
    fun deleteTask(taskId: String) {}
    fun clearAllTasks() { _state.value = _state.value.copy(tasks = emptyList()) }
}
