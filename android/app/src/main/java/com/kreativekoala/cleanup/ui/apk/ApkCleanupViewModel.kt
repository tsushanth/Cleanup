package com.kreativekoala.cleanup.ui.apk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreativekoala.cleanup.domain.service.ApkCleanupService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ApkCleanupViewModel @Inject constructor(
    private val apkCleanupService: ApkCleanupService
) : ViewModel() {

    data class UiState(
        val apkFiles: List<ApkCleanupService.ApkFile> = emptyList(),
        val selectedFiles: Set<String> = emptySet(),
        val isScanning: Boolean = false,
        val isDeleting: Boolean = false,
        val deletedCount: Int = 0,
        val filterStatus: ApkCleanupService.ApkStatus? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val filteredFiles: List<ApkCleanupService.ApkFile>
        get() {
            val state = _uiState.value
            return if (state.filterStatus == null) state.apkFiles
            else state.apkFiles.filter { it.status == state.filterStatus }
        }

    fun scan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            val files = apkCleanupService.scan()
            _uiState.update { it.copy(apkFiles = files, isScanning = false) }
        }
    }

    fun setFilter(status: ApkCleanupService.ApkStatus?) {
        _uiState.update { it.copy(filterStatus = status) }
    }

    fun toggleFile(path: String) {
        _uiState.update { state ->
            val newSet = state.selectedFiles.toMutableSet()
            if (path in newSet) newSet.remove(path) else newSet.add(path)
            state.copy(selectedFiles = newSet)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedFiles = filteredFiles.map { it.path }.toSet())
        }
    }

    fun deselectAll() {
        _uiState.update { it.copy(selectedFiles = emptySet()) }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val state = _uiState.value
            val filesToDelete = state.apkFiles.filter { it.path in state.selectedFiles }
            _uiState.update { it.copy(isDeleting = true) }
            val count = apkCleanupService.deleteApks(filesToDelete)
            _uiState.update { it.copy(isDeleting = false, deletedCount = count, selectedFiles = emptySet()) }
            scan()
        }
    }
}
