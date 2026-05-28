package com.aura.ai.presentation.screens.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VideoScene(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val uri: Uri,
    val duration: Float = 0f,
    val trimStart: Float = 0f,
    val trimEnd: Float = 0f,
    val hasText: Boolean = false,
    val textContent: String = "",
    val textPosition: String = "bottom",
    val transition: String = "cut",
    val hasAudio: Boolean = false,
    val audioUri: Uri? = null
)

data class EditorState(
    val scenes: List<VideoScene> = emptyList(),
    val selectedSceneIndex: Int = -1,
    val currentScene: VideoScene? = null,
    val isPlaying: Boolean = false,
    val isExporting: Boolean = false,
    val exportProgress: Int = 0,
    val exportQuality: String = "1080p",
    val formattedDuration: String = "0:00",
    val showTrimDialog: Boolean = false,
    val showTextDialog: Boolean = false,
    val showTransitionDialog: Boolean = false,
    val showAudioDialog: Boolean = false,
    val showExportDialog: Boolean = false,
    val trimStart: Float = 0f,
    val trimEnd: Float = 0f
)

class VideoEditorViewModel : ViewModel() {
    
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()
    
    fun addScene(uri: Uri? = null) {
        val scenes = _state.value.scenes.toMutableList()
        scenes.add(VideoScene(name = "Scene ${scenes.size + 1}", uri = uri ?: Uri.EMPTY))
        _state.value = _state.value.copy(scenes = scenes)
        updateDuration()
    }
    
    fun deleteScene(index: Int) {
        val scenes = _state.value.scenes.toMutableList()
        if (index in scenes.indices) {
            scenes.removeAt(index)
            _state.value = _state.value.copy(
                scenes = scenes,
                selectedSceneIndex = if (index == _state.value.selectedSceneIndex) -1 else _state.value.selectedSceneIndex
            )
            updateDuration()
        }
    }
    
    fun selectScene(index: Int) {
        _state.value = _state.value.copy(
            selectedSceneIndex = index,
            currentScene = _state.value.scenes.getOrNull(index)
        )
    }
    
    fun moveScene(from: Int, to: Int) {
        val scenes = _state.value.scenes.toMutableList()
        if (from in scenes.indices && to in scenes.indices) {
            val scene = scenes.removeAt(from)
            scenes.add(to, scene)
            _state.value = _state.value.copy(scenes = scenes)
        }
    }
    
    fun togglePlay() {
        _state.value = _state.value.copy(isPlaying = !_state.value.isPlaying)
    }
    
    fun nextScene() {
        val next = (_state.value.selectedSceneIndex + 1).coerceAtMost(_state.value.scenes.size - 1)
        selectScene(next)
    }
    
    fun previousScene() {
        val prev = (_state.value.selectedSceneIndex - 1).coerceAtLeast(0)
        selectScene(prev)
    }
    
    fun showTrimDialog(index: Int, isStart: Boolean) {
        val scene = _state.value.scenes.getOrNull(index) ?: return
        _state.value = _state.value.copy(
            showTrimDialog = true,
            selectedSceneIndex = index,
            trimStart = scene.trimStart,
            trimEnd = scene.trimEnd
        )
    }
    
    fun applyTrim(start: Float, end: Float) {
        val scenes = _state.value.scenes.toMutableList()
        val index = _state.value.selectedSceneIndex
        if (index in scenes.indices) {
            scenes[index] = scenes[index].copy(trimStart = start, trimEnd = end)
            _state.value = _state.value.copy(scenes = scenes, showTrimDialog = false)
        }
    }
    
    fun showAddTextDialog(index: Int) {
        _state.value = _state.value.copy(showTextDialog = true, selectedSceneIndex = index)
    }
    
    fun addTextOverlay(text: String, position: String) {
        val scenes = _state.value.scenes.toMutableList()
        val index = _state.value.selectedSceneIndex
        if (index in scenes.indices) {
            scenes[index] = scenes[index].copy(hasText = true, textContent = text, textPosition = position)
            _state.value = _state.value.copy(scenes = scenes, showTextDialog = false)
        }
    }
    
    fun showTransitionDialog(index: Int) {
        _state.value = _state.value.copy(showTransitionDialog = true, selectedSceneIndex = index)
    }
    
    fun addTransition(transition: String) {
        val scenes = _state.value.scenes.toMutableList()
        val index = _state.value.selectedSceneIndex
        if (index in scenes.indices) {
            scenes[index] = scenes[index].copy(transition = transition)
            _state.value = _state.value.copy(scenes = scenes, showTransitionDialog = false)
        }
    }
    
    fun showAddAudioDialog() {
        _state.value = _state.value.copy(showAudioDialog = true)
    }
    
    fun addAudio(audioUri: Uri) {
        val scenes = _state.value.scenes.toMutableList()
        val index = _state.value.selectedSceneIndex
        if (index in scenes.indices && index >= 0) {
            scenes[index] = scenes[index].copy(hasAudio = true, audioUri = audioUri)
            _state.value = _state.value.copy(scenes = scenes, showAudioDialog = false)
        }
    }
    
    fun exportVideo() {
        _state.value = _state.value.copy(showExportDialog = true)
    }
    
    fun startExport(quality: String) {
        _state.value = _state.value.copy(isExporting = true, exportQuality = quality, showExportDialog = false)
        viewModelScope.launch {
            for (i in 0..100 step 10) {
                kotlinx.coroutines.delay(500)
                _state.value = _state.value.copy(exportProgress = i)
            }
            _state.value = _state.value.copy(isExporting = false, exportProgress = 100)
        }
    }
    
    fun dismissDialogs() {
        _state.value = _state.value.copy(
            showTrimDialog = false, showTextDialog = false,
            showTransitionDialog = false, showAudioDialog = false, showExportDialog = false
        )
    }
    
    private fun updateDuration() {
        val total = _state.value.scenes.sumOf { it.duration.toDouble() }
        val minutes = (total / 60).toInt()
        val seconds = (total % 60).toInt()
        _state.value = _state.value.copy(formattedDuration = "$minutes:${seconds.toString().padStart(2, '0')}")
    }
}
