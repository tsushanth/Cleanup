package com.kreativekoala.cleanup.ui.archive

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreativekoala.cleanup.billing.EntitlementManager
import com.kreativekoala.cleanup.data.model.ArchiveQuota
import com.kreativekoala.cleanup.data.model.ArchivedItem
import com.kreativekoala.cleanup.domain.service.ArchiveAuthService
import com.kreativekoala.cleanup.domain.service.ArchiveService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Port of iOS ArchiveViewModel.
 */
@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val archiveService: ArchiveService,
    private val authService: ArchiveAuthService,
    private val entitlementManager: EntitlementManager
) : ViewModel() {

    data class UiState(
        val quota: ArchiveQuota? = null,
        val isLoading: Boolean = false,
        val showSignIn: Boolean = false,
        val showUpgradePaywall: Boolean = false,
        val showArchiveSuccess: Boolean = false,
        val archiveSuccessCount: Int = 0,
        val showRestoreSuccess: Boolean = false,
        val errorMessage: String? = null,
        val selectedFilter: ArchiveFilter = ArchiveFilter.ALL,
        val selectedItem: ArchivedItem? = null,
        val showDeleteConfirmation: Boolean = false
    )

    enum class ArchiveFilter(val displayName: String) {
        ALL("All"),
        PHOTOS("Photos"),
        VIDEOS("Videos"),
        CONTACTS("Contacts")
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val items: StateFlow<List<ArchivedItem>> = archiveService.getAllItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isAuthenticated: StateFlow<Boolean> = authService.isAuthenticated
    val isFullySetUp: StateFlow<Boolean> = authService.isFullySetUp

    init {
        loadData()
    }

    fun filteredItems(allItems: List<ArchivedItem>): List<ArchivedItem> {
        return when (_uiState.value.selectedFilter) {
            ArchiveFilter.ALL -> allItems
            ArchiveFilter.PHOTOS -> allItems.filter { it.fileType == "PHOTO" }
            ArchiveFilter.VIDEOS -> allItems.filter { it.fileType == "VIDEO" }
            ArchiveFilter.CONTACTS -> allItems.filter { it.fileType == "CONTACT" }
        }
    }

    // MARK: - Load

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val quota = archiveService.getQuota()
                _uiState.update { it.copy(quota = quota, isLoading = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun syncWithServer() {
        viewModelScope.launch {
            try {
                archiveService.syncItems()
                val quota = archiveService.getQuota()
                _uiState.update { it.copy(quota = quota) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }

    // MARK: - Archive

    fun archiveMedia(
        uri: Uri,
        fileName: String,
        fileType: String,
        fileSize: Long
    ) {
        viewModelScope.launch {
            if (!authService.isFullySetUp.value) {
                _uiState.update { it.copy(showSignIn = true) }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                authService.ensureRegistered()
                archiveService.archiveMedia(
                    uri = uri,
                    fileName = fileName,
                    fileType = fileType,
                    fileSize = fileSize,
                    deleteAfterArchive = true
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        showArchiveSuccess = true,
                        archiveSuccessCount = 1
                    )
                }
                refreshQuota()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Archive failed: ${e.localizedMessage}")
                }
            }
        }
    }

    fun onSignInComplete() {
        _uiState.update { it.copy(showSignIn = false) }
    }

    fun signIn(googleIdToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                authService.signIn(googleIdToken)
                _uiState.update { it.copy(isLoading = false, showSignIn = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Sign in failed: ${e.localizedMessage}")
                }
            }
        }
    }

    fun signOut() {
        authService.signOut()
    }

    // MARK: - Retrieval

    fun initiateRetrieval(item: ArchivedItem) {
        viewModelScope.launch {
            try {
                val response = archiveService.initiateRetrieval(item.id)
                if (response.status == "available" && response.downloadURL != null) {
                    archiveService.downloadAndRestore(item.id, response.downloadURL)
                    archiveService.deleteArchivedItem(item.id)
                    _uiState.update {
                        it.copy(showRestoreSuccess = true, selectedItem = null)
                    }
                }
                refreshQuota()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }

    // MARK: - Delete

    fun selectItem(item: ArchivedItem) {
        _uiState.update { it.copy(selectedItem = item) }
    }

    fun clearSelectedItem() {
        _uiState.update { it.copy(selectedItem = null) }
    }

    fun showDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = true) }
    }

    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun deleteSelectedItem() {
        val item = _uiState.value.selectedItem ?: return
        viewModelScope.launch {
            try {
                archiveService.deleteArchivedItem(item.id)
                _uiState.update {
                    it.copy(selectedItem = null, showDeleteConfirmation = false)
                }
                refreshQuota()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }

    // MARK: - Filter

    fun setFilter(filter: ArchiveFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    // MARK: - Dismiss

    fun dismissSuccess() {
        _uiState.update { it.copy(showArchiveSuccess = false, showRestoreSuccess = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun showUpgradePaywall() {
        _uiState.update { it.copy(showUpgradePaywall = true) }
    }

    fun dismissUpgradePaywall() {
        _uiState.update { it.copy(showUpgradePaywall = false) }
    }

    private fun refreshQuota() {
        viewModelScope.launch {
            try {
                val quota = archiveService.getQuota()
                _uiState.update { it.copy(quota = quota) }
            } catch (_: Exception) {}
        }
    }
}
