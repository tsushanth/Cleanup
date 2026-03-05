package com.kreativekoala.cleanup.domain.service

import com.kreativekoala.cleanup.data.model.DuplicateScanResult
import com.kreativekoala.cleanup.data.model.PhotoAsset
import com.kreativekoala.cleanup.data.model.ScanResult
import com.kreativekoala.cleanup.data.model.SimilarScanResult
import com.kreativekoala.cleanup.data.model.VideoAsset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared in-memory cache for scan results.
 * HomeViewModel populates this during the initial scan;
 * detail screens (Photos, Videos) read from it to avoid re-scanning.
 */
@Singleton
class ScanResultsCache @Inject constructor() {

    // Raw loaded media
    var allPhotos: List<PhotoAsset>? = null
        private set
    var allVideosAsPhotos: List<PhotoAsset>? = null
        private set
    var allVideoAssets: List<VideoAsset>? = null
        private set

    // Analysis results
    var duplicateResult: DuplicateScanResult? = null
        private set
    var similarResult: SimilarScanResult? = null
        private set
    var screenshotResult: ScanResult? = null
        private set
    var largeVideoResult: ScanResult? = null
        private set

    fun storePhotoResults(
        photos: List<PhotoAsset>,
        duplicates: DuplicateScanResult,
        similar: SimilarScanResult,
        screenshots: ScanResult
    ) {
        allPhotos = photos
        duplicateResult = duplicates
        similarResult = similar
        screenshotResult = screenshots
    }

    fun storeVideoResults(
        videosAsPhotos: List<PhotoAsset>,
        videoAssets: List<VideoAsset>,
        largeVideos: ScanResult
    ) {
        allVideosAsPhotos = videosAsPhotos
        allVideoAssets = videoAssets
        largeVideoResult = largeVideos
    }

    fun invalidatePhotos() {
        allPhotos = null
        duplicateResult = null
        similarResult = null
        screenshotResult = null
    }

    fun invalidateVideos() {
        allVideosAsPhotos = null
        allVideoAssets = null
        largeVideoResult = null
    }

    fun invalidateAll() {
        invalidatePhotos()
        invalidateVideos()
    }
}
