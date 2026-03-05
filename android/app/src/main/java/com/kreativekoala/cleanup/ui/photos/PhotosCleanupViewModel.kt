package com.kreativekoala.cleanup.ui.photos

import android.app.PendingIntent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreativekoala.cleanup.billing.PaywallCoordinator
import com.kreativekoala.cleanup.billing.PaywallContext
import com.kreativekoala.cleanup.data.model.DuplicateGroup
import com.kreativekoala.cleanup.data.model.PhotoAsset
import com.kreativekoala.cleanup.data.model.SimilarPhotoGroup
import com.kreativekoala.cleanup.domain.service.MediaAccessHelper
import com.kreativekoala.cleanup.domain.service.PhotoAnalysisService
import com.kreativekoala.cleanup.domain.service.ScanResultsCache
import com.kreativekoala.cleanup.util.ByteFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhotosCleanupViewModel @Inject constructor(
    private val photoAnalysisService: PhotoAnalysisService,
    private val mediaAccessHelper: MediaAccessHelper,
    private val paywallCoordinator: PaywallCoordinator,
    private val scanResultsCache: ScanResultsCache
) : ViewModel() {

    data class UiState(
        val duplicateGroups: List<DuplicateGroup> = emptyList(),
        val similarGroups: List<SimilarPhotoGroup> = emptyList(),
        val screenshots: List<PhotoAsset> = emptyList(),
        val selectedAssets: Set<Long> = emptySet(),
        val isScanning: Boolean = false,
        val scanProgress: Float = 0f,
        val showDeleteConfirmation: Boolean = false,
        val deleteRequestPendingIntent: PendingIntent? = null,
        val showPaywall: Boolean = false,
        val hasMediaAccess: Boolean = false,
        val photoCount: Int = 0
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loadedPhotos: List<PhotoAsset> = emptyList()

    val selectedCount: Int get() = _uiState.value.selectedAssets.size

    val totalSavings: Long
        get() {
            val state = _uiState.value
            val selectedIds = state.selectedAssets
            var total = 0L

            for (group in state.duplicateGroups) {
                for (photo in group.photos) {
                    if (photo.id in selectedIds) total += photo.fileSize
                }
            }
            for (group in state.similarGroups) {
                for (photo in group.photos) {
                    if (photo.id in selectedIds) total += photo.fileSize
                }
            }
            for (photo in state.screenshots) {
                if (photo.id in selectedIds) total += photo.fileSize
            }

            return total
        }

    val totalSavingsFormatted: String get() = ByteFormatter.format(totalSavings)

    init {
        // Try to use cached results from the Home scan first
        val cachedDuplicates = scanResultsCache.duplicateResult
        val cachedSimilar = scanResultsCache.similarResult
        val cachedScreenshots = scanResultsCache.screenshotResult
        val cachedPhotos = scanResultsCache.allPhotos

        if (cachedDuplicates != null && cachedSimilar != null && cachedScreenshots != null && cachedPhotos != null) {
            loadedPhotos = cachedPhotos
            _uiState.update {
                it.copy(
                    hasMediaAccess = true,
                    photoCount = cachedPhotos.size,
                    duplicateGroups = cachedDuplicates.groups,
                    similarGroups = cachedSimilar.groups,
                    screenshots = cachedScreenshots.items
                )
            }
        } else if (mediaAccessHelper.hasFolderAccess()) {
            val uri = mediaAccessHelper.getSavedFolderUri()
            if (uri != null) {
                _uiState.update { it.copy(hasMediaAccess = true) }
                loadFromFolder(uri)
            }
        }
    }

    // MARK: - Media Loading

    fun onFolderSelected(treeUri: Uri) {
        mediaAccessHelper.saveFolderUri(treeUri)
        _uiState.update { it.copy(hasMediaAccess = true) }
        loadFromFolder(treeUri)
    }

    fun onPhotosSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _uiState.update { it.copy(hasMediaAccess = true) }
        loadFromPickerUris(uris)
    }

    private fun loadFromFolder(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanProgress = 0f) }

            val photos = mediaAccessHelper.loadPhotosFromFolder(treeUri)
            loadedPhotos = photos
            _uiState.update { it.copy(photoCount = photos.size, scanProgress = 0.2f) }

            runAnalysis(photos)
        }
    }

    private fun loadFromPickerUris(uris: List<Uri>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanProgress = 0f) }

            val photos = mediaAccessHelper.loadPhotosFromPickerUris(uris)
            loadedPhotos = photos
            _uiState.update { it.copy(photoCount = photos.size, scanProgress = 0.2f) }

            runAnalysis(photos)
        }
    }

    private suspend fun runAnalysis(photos: List<PhotoAsset>) {
        // Scan duplicates (0.2-0.5)
        _uiState.update { it.copy(scanProgress = 0.3f) }
        val duplicateResult = photoAnalysisService.findDuplicates(photos)
        _uiState.update {
            it.copy(duplicateGroups = duplicateResult.groups, scanProgress = 0.5f)
        }

        // Scan similar photos (0.5-0.8)
        _uiState.update { it.copy(scanProgress = 0.6f) }
        val similarResult = photoAnalysisService.findSimilarPhotos(photos)
        _uiState.update {
            it.copy(similarGroups = similarResult.groups, scanProgress = 0.8f)
        }

        // Find screenshots (0.8-1.0)
        _uiState.update { it.copy(scanProgress = 0.9f) }
        val screenshotResult = photoAnalysisService.findScreenshots(photos)
        _uiState.update {
            it.copy(
                screenshots = screenshotResult.items,
                scanProgress = 1.0f,
                isScanning = false
            )
        }
    }

    fun rescan() {
        val uri = mediaAccessHelper.getSavedFolderUri()
        if (uri != null && mediaAccessHelper.hasFolderAccess()) {
            loadFromFolder(uri)
        }
    }

    // MARK: - Selection

    fun isSelected(photoId: Long): Boolean = _uiState.value.selectedAssets.contains(photoId)

    fun toggleSelection(photo: PhotoAsset) {
        _uiState.update { state ->
            val newSet = state.selectedAssets.toMutableSet()
            if (photo.id in newSet) newSet.remove(photo.id) else newSet.add(photo.id)
            state.copy(selectedAssets = newSet)
        }
    }

    fun selectAll(group: DuplicateGroup) {
        _uiState.update { state ->
            val newSet = state.selectedAssets.toMutableSet()
            group.photos.forEach { newSet.add(it.id) }
            state.copy(selectedAssets = newSet)
        }
    }

    fun selectAllExceptBest(group: DuplicateGroup) {
        _uiState.update { state ->
            val newSet = state.selectedAssets.toMutableSet()
            group.photos.forEach { photo ->
                if (photo.id != group.bestPhoto.id) newSet.add(photo.id)
            }
            newSet.remove(group.bestPhoto.id)
            state.copy(selectedAssets = newSet)
        }
    }

    fun selectSuggestedToDelete(group: SimilarPhotoGroup) {
        _uiState.update { state ->
            val newSet = state.selectedAssets.toMutableSet()
            group.photos.forEach { photo ->
                if (photo.id != group.bestPhoto.id) newSet.add(photo.id)
            }
            newSet.remove(group.bestPhoto.id)
            state.copy(selectedAssets = newSet)
        }
    }

    fun selectAllScreenshots() {
        _uiState.update { state ->
            val newSet = state.selectedAssets.toMutableSet()
            state.screenshots.forEach { newSet.add(it.id) }
            state.copy(selectedAssets = newSet)
        }
    }

    fun deselectAllScreenshots() {
        _uiState.update { state ->
            val newSet = state.selectedAssets.toMutableSet()
            state.screenshots.forEach { newSet.remove(it.id) }
            state.copy(selectedAssets = newSet)
        }
    }

    fun selectAllDuplicates() {
        _uiState.update { state ->
            val newSet = state.selectedAssets.toMutableSet()
            state.duplicateGroups.forEach { group ->
                group.photos.forEach { photo ->
                    if (photo.id != group.bestPhoto.id) newSet.add(photo.id)
                }
            }
            state.copy(selectedAssets = newSet)
        }
    }

    fun deselectAllDuplicates() {
        _uiState.update { state ->
            val newSet = state.selectedAssets.toMutableSet()
            state.duplicateGroups.forEach { group ->
                group.photos.forEach { newSet.remove(it.id) }
            }
            state.copy(selectedAssets = newSet)
        }
    }

    fun selectAllSimilar() {
        _uiState.update { state ->
            val newSet = state.selectedAssets.toMutableSet()
            state.similarGroups.forEach { group ->
                group.photos.forEach { photo ->
                    if (photo.id != group.bestPhoto.id) newSet.add(photo.id)
                }
            }
            state.copy(selectedAssets = newSet)
        }
    }

    fun deselectAllSimilar() {
        _uiState.update { state ->
            val newSet = state.selectedAssets.toMutableSet()
            state.similarGroups.forEach { group ->
                group.photos.forEach { newSet.remove(it.id) }
            }
            state.copy(selectedAssets = newSet)
        }
    }

    fun getSelectedPhotoAssets(): List<PhotoAsset> {
        val state = _uiState.value
        val selectedIds = state.selectedAssets
        val result = mutableListOf<PhotoAsset>()

        for (group in state.duplicateGroups) {
            for (photo in group.photos) {
                if (photo.id in selectedIds) result.add(photo)
            }
        }
        for (group in state.similarGroups) {
            for (photo in group.photos) {
                if (photo.id in selectedIds) result.add(photo)
            }
        }
        for (photo in state.screenshots) {
            if (photo.id in selectedIds) result.add(photo)
        }

        return result
    }

    // MARK: - Deletion

    private var pendingDeleteCount = 0

    fun requestDelete(currentTab: Int) {
        val count = selectedCount
        val savedGB = totalSavings / 1_000_000_000.0
        val context = when (currentTab) {
            0 -> PaywallContext.AttemptDeleteDuplicates(count, savedGB)
            1 -> PaywallContext.AttemptDeleteSimilar(count, savedGB)
            else -> PaywallContext.AttemptDeleteScreenshots(count, savedGB)
        }
        val access = paywallCoordinator.checkAccess(context = context)
        if (access == PaywallCoordinator.AccessResult.PAYWALL) {
            _uiState.update { it.copy(showPaywall = true) }
            return
        }
        pendingDeleteCount = count
        _uiState.update { it.copy(showDeleteConfirmation = true) }
    }

    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun dismissPaywall() {
        _uiState.update { it.copy(showPaywall = false) }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val assetsToDelete = getSelectedPhotoAssets()
            if (assetsToDelete.isEmpty()) return@launch

            val pendingIntent = photoAnalysisService.createDeleteRequest(assetsToDelete)

            if (pendingIntent != null) {
                // API 30+: Need to launch the system consent dialog
                _uiState.update {
                    it.copy(
                        deleteRequestPendingIntent = pendingIntent,
                        showDeleteConfirmation = false
                    )
                }
            } else {
                // Deleted directly (SAF or API 29-)
                onDeleteCompleted()
            }
        }
    }

    fun onDeleteCompleted() {
        if (pendingDeleteCount > 0) {
            paywallCoordinator.recordUsage(pendingDeleteCount)
            pendingDeleteCount = 0
        }

        val deletedIds = _uiState.value.selectedAssets

        // Remove deleted items from current state instead of rescanning
        loadedPhotos = loadedPhotos.filter { it.id !in deletedIds }
        _uiState.update { state ->
            state.copy(
                selectedAssets = emptySet(),
                deleteRequestPendingIntent = null,
                showDeleteConfirmation = false,
                photoCount = loadedPhotos.size,
                duplicateGroups = state.duplicateGroups.mapNotNull { group ->
                    val remaining = group.photos.filter { it.id !in deletedIds }
                    if (remaining.size >= 2) group.copy(photos = remaining) else null
                },
                similarGroups = state.similarGroups.mapNotNull { group ->
                    val remaining = group.photos.filter { it.id !in deletedIds }
                    if (remaining.size >= 2) group.copy(photos = remaining) else null
                },
                screenshots = state.screenshots.filter { it.id !in deletedIds }
            )
        }

        scanResultsCache.invalidatePhotos()
    }

    fun clearDeletePendingIntent() {
        _uiState.update { it.copy(deleteRequestPendingIntent = null) }
    }
}
