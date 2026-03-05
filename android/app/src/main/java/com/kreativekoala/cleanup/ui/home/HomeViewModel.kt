package com.kreativekoala.cleanup.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreativekoala.cleanup.domain.service.MediaAccessHelper
import com.kreativekoala.cleanup.domain.service.PhotoAnalysisService
import com.kreativekoala.cleanup.domain.service.ScanResultsCache
import com.kreativekoala.cleanup.domain.service.StorageService
import com.kreativekoala.cleanup.util.ByteFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val storageService: StorageService,
    private val photoAnalysisService: PhotoAnalysisService,
    private val mediaAccessHelper: MediaAccessHelper,
    private val scanResultsCache: ScanResultsCache
) : ViewModel() {

    data class UiState(
        val storageUsedFormatted: String = "",
        val storageTotalFormatted: String = "",
        val storageUsedPercentage: Double = 0.0,
        val duplicatePhotosCount: Int = 0,
        val duplicatePhotosSavings: String = "",
        val similarPhotosCount: Int = 0,
        val similarPhotosSavings: String = "",
        val screenshotsCount: Int = 0,
        val screenshotsSavings: String = "",
        val largeVideosCount: Int = 0,
        val largeVideosSavings: String = "",
        val duplicateContactsCount: Int = 0,
        val isScanning: Boolean = false,
        val scanProgress: Float = 0f,
        val hasFolderAccess: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadStorageInfo()
        _uiState.update { it.copy(hasFolderAccess = mediaAccessHelper.hasFolderAccess()) }

        // Auto-scan if we have persistent folder access
        if (mediaAccessHelper.hasFolderAccess()) {
            val uri = mediaAccessHelper.getSavedFolderUri()
            if (uri != null) scanAll(uri)
        }
    }

    private fun loadStorageInfo() {
        viewModelScope.launch {
            val info = storageService.getStorageInfo()
            _uiState.update {
                it.copy(
                    storageUsedFormatted = ByteFormatter.format(info.usedBytes),
                    storageTotalFormatted = ByteFormatter.format(info.totalBytes),
                    storageUsedPercentage = info.usedPercentage
                )
            }
        }
    }

    fun onFolderSelected(treeUri: Uri) {
        mediaAccessHelper.saveFolderUri(treeUri)
        _uiState.update { it.copy(hasFolderAccess = true) }
        scanAll(treeUri)
    }

    private fun scanAll(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanProgress = 0f) }

            // Load photos from folder
            _uiState.update { it.copy(scanProgress = 0.1f) }
            val allPhotos = mediaAccessHelper.loadPhotosFromFolder(treeUri)
            val allVideos = mediaAccessHelper.loadVideosFromFolder(treeUri)
            val allVideoAssets = mediaAccessHelper.loadVideoAssetsFromFolder(treeUri)

            // Scan duplicates
            _uiState.update { it.copy(scanProgress = 0.3f) }
            val duplicateResult = photoAnalysisService.findDuplicates(allPhotos)
            _uiState.update {
                it.copy(
                    duplicatePhotosCount = duplicateResult.totalDuplicates,
                    duplicatePhotosSavings = ByteFormatter.format(duplicateResult.potentialSavings),
                    scanProgress = 0.5f
                )
            }

            // Scan similar photos
            _uiState.update { it.copy(scanProgress = 0.5f) }
            val similarResult = photoAnalysisService.findSimilarPhotos(allPhotos)
            _uiState.update {
                it.copy(
                    similarPhotosCount = similarResult.totalSimilar,
                    similarPhotosSavings = ByteFormatter.format(similarResult.potentialSavings),
                    scanProgress = 0.6f
                )
            }

            // Scan screenshots
            _uiState.update { it.copy(scanProgress = 0.7f) }
            val screenshotResult = photoAnalysisService.findScreenshots(allPhotos)
            _uiState.update {
                it.copy(
                    screenshotsCount = screenshotResult.totalCount,
                    screenshotsSavings = ByteFormatter.format(screenshotResult.potentialSavings),
                    scanProgress = 0.8f
                )
            }

            // Scan large videos
            _uiState.update { it.copy(scanProgress = 0.9f) }
            val largeVideoResult = photoAnalysisService.findLargeVideos(allVideos)
            _uiState.update {
                it.copy(
                    largeVideosCount = largeVideoResult.totalCount,
                    largeVideosSavings = ByteFormatter.format(largeVideoResult.potentialSavings),
                    scanProgress = 1.0f,
                    isScanning = false
                )
            }

            // Cache results so detail screens don't re-scan
            scanResultsCache.storePhotoResults(allPhotos, duplicateResult, similarResult, screenshotResult)
            scanResultsCache.storeVideoResults(allVideos, allVideoAssets, largeVideoResult)
        }
    }

    fun performOneTapCleanup() {
        if (_uiState.value.isScanning) return
        val uri = mediaAccessHelper.getSavedFolderUri()
        if (uri != null && mediaAccessHelper.hasFolderAccess()) {
            scanAll(uri)
        }
    }
}
