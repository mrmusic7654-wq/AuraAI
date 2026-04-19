package com.aura.ai.presentation.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.ai.data.local.dao.TaskDao
import com.aura.ai.data.local.entities.TaskEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val taskDao: TaskDao
) : ViewModel() {
    
    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()
    
    init {
        loadTasks()
    }
    
    private fun loadTasks() {
        viewModelScope.launch {
            taskDao.getAllTasks().collect { tasks ->
                _state.update { it.copy(tasks = tasks) }
            }
        }
    }
    
    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            taskDao.deleteTask(taskId)
        }
    }
    
    fun clearAllTasks() {
        viewModelScope.launch {
            taskDao.clearAllTasks()
        }
    }
    
    fun selectTask(task: TaskEntity?) {
        _state.update { it.copy(selectedTask = task) }
    }
}
