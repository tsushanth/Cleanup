package com.kreativekoala.cleanup.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreativekoala.cleanup.domain.service.DownloadCleanupService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadCleanupViewModel @Inject constructor(
    private val downloadCleanupService: DownloadCleanupService
) : ViewModel() {

    data class UiState(
        val scanResult: DownloadCleanupService.DownloadScanResult? = null,
        val selectedFiles: Set<Long> = emptySet(),
        val isScanning: Boolean = false,
        val isDeleting: Boolean = false,
        val deletedCount: Int = 0,
        val activeTab: Tab = Tab.ALL
    )

    enum class Tab(val label: String) {
        ALL("All"),
        LARGE("Large"),
        OLD("Old")
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val displayedFiles: List<DownloadCleanupService.DownloadFile>
        get() {
            val state = _uiState.value
            val result = state.scanResult ?: return emptyList()
            return when (state.activeTab) {
                Tab.ALL -> result.allFiles
                Tab.LARGE -> result.largeFiles
                Tab.OLD -> result.oldFiles
            }
        }

    fun scan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            val result = downloadCleanupService.scan()
            _uiState.update { it.copy(scanResult = result, isScanning = false) }
        }
    }

    fun setTab(tab: Tab) {
        _uiState.update { it.copy(activeTab = tab, selectedFiles = emptySet()) }
    }

    fun toggleFile(id: Long) {
        _uiState.update { state ->
            val newSet = state.selectedFiles.toMutableSet()
            if (id in newSet) newSet.remove(id) else newSet.add(id)
            state.copy(selectedFiles = newSet)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedFiles = displayedFiles.map { it.id }.toSet())
        }
    }

    fun deselectAll() {
        _uiState.update { it.copy(selectedFiles = emptySet()) }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val state = _uiState.value
            val result = state.scanResult ?: return@launch
            val filesToDelete = result.allFiles.filter { it.id in state.selectedFiles }
            _uiState.update { it.copy(isDeleting = true) }
            val count = downloadCleanupService.deleteFiles(filesToDelete)
            _uiState.update { it.copy(isDeleting = false, deletedCount = count, selectedFiles = emptySet()) }
            scan()
        }
    }
}
