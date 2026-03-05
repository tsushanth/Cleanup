package com.kreativekoala.cleanup.ui.videos

import android.app.PendingIntent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreativekoala.cleanup.billing.PaywallCoordinator
import com.kreativekoala.cleanup.billing.PaywallContext
import com.kreativekoala.cleanup.data.model.CompressionQuality
import com.kreativekoala.cleanup.data.model.PhotoAsset
import com.kreativekoala.cleanup.data.model.VideoAsset
import com.kreativekoala.cleanup.domain.service.MediaAccessHelper
import com.kreativekoala.cleanup.domain.service.PhotoAnalysisService
import com.kreativekoala.cleanup.domain.service.ScanResultsCache
import com.kreativekoala.cleanup.domain.service.VideoCompressionService
import com.kreativekoala.cleanup.util.ByteFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VideosCleanupViewModel @Inject constructor(
    private val photoAnalysisService: PhotoAnalysisService,
    private val videoCompressionService: VideoCompressionService,
    private val mediaAccessHelper: MediaAccessHelper,
    private val paywallCoordinator: PaywallCoordinator,
    private val scanResultsCache: ScanResultsCache
) : ViewModel() {

    data class UiState(
        val largeVideos: List<PhotoAsset> = emptyList(),
        val videosToCompress: List<VideoAsset> = emptyList(),
        val selectedVideoIds: Set<Long> = emptySet(),
        val selectedCompressionIds: Set<Long> = emptySet(),
        val isScanning: Boolean = false,
        val scanProgress: Float = 0f,
        val showDeleteConfirmation: Boolean = false,
        val deleteRequestPendingIntent: PendingIntent? = null,
        val isCompressing: Boolean = false,
        val compressionProgress: Float = 0f,
        val currentCompressionVideo: String = "",
        val selectedQuality: CompressionQuality = CompressionQuality.MEDIUM,
        val showPaywall: Boolean = false,
        val hasMediaAccess: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val selectedCount: Int get() = _uiState.value.selectedVideoIds.size

    val totalSavingsFormatted: String
        get() {
            val state = _uiState.value
            val total = state.largeVideos
                .filter { it.id in state.selectedVideoIds }
                .sumOf { it.fileSize }
            return ByteFormatter.format(total)
        }

    val totalSizeFormatted: String
        get() = ByteFormatter.format(_uiState.value.largeVideos.sumOf { it.fileSize })

    init {
        // Try to use cached results from the Home scan first
        val cachedLargeVideos = scanResultsCache.largeVideoResult
        val cachedVideoAssets = scanResultsCache.allVideoAssets

        if (cachedLargeVideos != null && cachedVideoAssets != null) {
            _uiState.update {
                it.copy(
                    hasMediaAccess = true,
                    largeVideos = cachedLargeVideos.items,
                    videosToCompress = cachedVideoAssets.filter { v -> v.fileSize > 10_000_000 }
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

    fun onVideosSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _uiState.update { it.copy(hasMediaAccess = true) }
        loadFromPickerUris(uris)
    }

    private fun loadFromFolder(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanProgress = 0f) }

            val videoAsPhotos = mediaAccessHelper.loadVideosFromFolder(treeUri)
            val videoAssets = mediaAccessHelper.loadVideoAssetsFromFolder(treeUri)

            val largeResult = photoAnalysisService.findLargeVideos(videoAsPhotos)

            _uiState.update {
                it.copy(
                    largeVideos = largeResult.items,
                    videosToCompress = videoAssets.filter { v -> v.fileSize > 10_000_000 },
                    scanProgress = 1.0f,
                    isScanning = false
                )
            }
        }
    }

    private fun loadFromPickerUris(uris: List<Uri>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanProgress = 0f) }

            val videoAssets = mediaAccessHelper.loadVideoAssetsFromPickerUris(uris)
            val videoAsPhotos = videoAssets.map { v ->
                PhotoAsset(
                    id = v.id,
                    uri = v.uri,
                    dateAdded = v.dateAdded,
                    dateTaken = v.dateAdded * 1000,
                    fileSize = v.fileSize,
                    width = v.width,
                    height = v.height,
                    displayName = v.displayName,
                    mimeType = v.mimeType
                )
            }

            val largeResult = photoAnalysisService.findLargeVideos(videoAsPhotos)

            _uiState.update {
                it.copy(
                    largeVideos = largeResult.items,
                    videosToCompress = videoAssets.filter { v -> v.fileSize > 10_000_000 },
                    scanProgress = 1.0f,
                    isScanning = false
                )
            }
        }
    }

    fun rescan() {
        val uri = mediaAccessHelper.getSavedFolderUri()
        if (uri != null && mediaAccessHelper.hasFolderAccess()) {
            loadFromFolder(uri)
        }
    }

    // MARK: - Large Videos Selection

    fun isSelected(videoId: Long): Boolean = _uiState.value.selectedVideoIds.contains(videoId)

    fun toggleSelection(video: PhotoAsset) {
        _uiState.update { state ->
            val newSet = state.selectedVideoIds.toMutableSet()
            if (video.id in newSet) newSet.remove(video.id) else newSet.add(video.id)
            state.copy(selectedVideoIds = newSet)
        }
    }

    fun selectAllVideos() {
        _uiState.update { state ->
            val newSet = state.selectedVideoIds.toMutableSet()
            state.largeVideos.forEach { newSet.add(it.id) }
            state.copy(selectedVideoIds = newSet)
        }
    }

    fun deselectAllVideos() {
        _uiState.update { state ->
            state.copy(selectedVideoIds = emptySet())
        }
    }

    // MARK: - Deletion

    private var pendingDeleteCount = 0

    fun requestDelete() {
        val count = selectedCount
        val savedGB = _uiState.value.largeVideos
            .filter { it.id in _uiState.value.selectedVideoIds }
            .sumOf { it.fileSize } / 1_000_000_000.0
        val access = paywallCoordinator.checkAccess(
            context = PaywallContext.AttemptDeleteVideos(count, savedGB)
        )
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
            val state = _uiState.value
            val toDelete = state.largeVideos.filter { it.id in state.selectedVideoIds }
            if (toDelete.isEmpty()) return@launch

            val pendingIntent = photoAnalysisService.createDeleteRequest(toDelete)
            if (pendingIntent != null) {
                _uiState.update {
                    it.copy(deleteRequestPendingIntent = pendingIntent, showDeleteConfirmation = false)
                }
            } else {
                onDeleteCompleted()
            }
        }
    }

    fun onDeleteCompleted() {
        if (pendingDeleteCount > 0) {
            paywallCoordinator.recordUsage(pendingDeleteCount)
            pendingDeleteCount = 0
        }

        val deletedIds = _uiState.value.selectedVideoIds

        // Remove deleted items from current state instead of rescanning
        _uiState.update { state ->
            state.copy(
                selectedVideoIds = emptySet(),
                deleteRequestPendingIntent = null,
                showDeleteConfirmation = false,
                largeVideos = state.largeVideos.filter { it.id !in deletedIds },
                videosToCompress = state.videosToCompress.filter { it.id !in deletedIds }
            )
        }

        scanResultsCache.invalidateVideos()
    }

    fun clearDeletePendingIntent() {
        _uiState.update { it.copy(deleteRequestPendingIntent = null) }
    }

    // MARK: - Compression

    fun isCompressionSelected(videoId: Long): Boolean =
        _uiState.value.selectedCompressionIds.contains(videoId)

    fun toggleCompressionSelection(video: VideoAsset) {
        _uiState.update { state ->
            val newSet = state.selectedCompressionIds.toMutableSet()
            if (video.id in newSet) newSet.remove(video.id) else newSet.add(video.id)
            state.copy(selectedCompressionIds = newSet)
        }
    }

    fun setQuality(quality: CompressionQuality) {
        _uiState.update { it.copy(selectedQuality = quality) }
    }

    fun estimatedSavings(video: VideoAsset): String {
        val savings = videoCompressionService.estimateSavings(
            video.fileSize,
            _uiState.value.selectedQuality
        )
        return ByteFormatter.format(savings)
    }

    fun estimatedCompressedSize(video: VideoAsset): String {
        val compressed = videoCompressionService.estimateCompressedSize(
            video.fileSize,
            _uiState.value.selectedQuality
        )
        return ByteFormatter.format(compressed)
    }

    fun compressSelected() {
        val state = _uiState.value
        val selected = state.videosToCompress.filter { it.id in state.selectedCompressionIds }
        if (selected.isEmpty()) return

        val savedGB = selected.sumOf {
            videoCompressionService.estimateSavings(it.fileSize, state.selectedQuality)
        } / 1_000_000_000.0
        val access = paywallCoordinator.checkAccess(
            context = PaywallContext.AttemptCompressVideo(savedGB)
        )
        if (access == PaywallCoordinator.AccessResult.PAYWALL) {
            _uiState.update { it.copy(showPaywall = true) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCompressing = true, compressionProgress = 0f) }

            for ((index, video) in selected.withIndex()) {
                _uiState.update {
                    it.copy(
                        currentCompressionVideo = "Video ${index + 1} of ${selected.size}",
                        compressionProgress = 0f
                    )
                }

                videoCompressionService.compressVideo(
                    video = video,
                    quality = state.selectedQuality,
                    onProgress = { progress ->
                        _uiState.update { it.copy(compressionProgress = progress) }
                    }
                )
            }

            videoCompressionService.cleanupTempFiles()
            paywallCoordinator.recordUsage(selected.size)
            _uiState.update {
                it.copy(isCompressing = false, selectedCompressionIds = emptySet())
            }
            rescan()
        }
    }
}
