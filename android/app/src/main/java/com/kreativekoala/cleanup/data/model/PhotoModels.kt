package com.kreativekoala.cleanup.data.model

import androidx.annotation.StringRes
import com.kreativekoala.cleanup.R
import android.net.Uri

data class PhotoAsset(
    val id: Long,
    val uri: Uri,
    val dateAdded: Long,
    val dateTaken: Long,
    val fileSize: Long,
    val width: Int,
    val height: Int,
    val displayName: String,
    val mimeType: String,
    val relativePath: String = ""
)

data class DuplicateGroup(
    val hash: String,
    val photos: List<PhotoAsset>,
    val bestPhoto: PhotoAsset
) {
    val duplicateCount: Int get() = photos.size - 1
    val potentialSavings: Long get() = photos.drop(1).sumOf { it.fileSize }
}

data class SimilarPhotoGroup(
    val photos: List<PhotoAsset>,
    val bestPhoto: PhotoAsset,
    val similarityScore: Float
) {
    val similarCount: Int get() = photos.size - 1
    val potentialSavings: Long get() = photos.filter { it.id != bestPhoto.id }.sumOf { it.fileSize }
}

data class VideoAsset(
    val id: Long,
    val uri: Uri,
    val dateAdded: Long,
    val fileSize: Long,
    val duration: Long, // milliseconds
    val width: Int,
    val height: Int,
    val displayName: String,
    val mimeType: String
)

data class ScanResult(
    val items: List<PhotoAsset>,
    val totalCount: Int,
    val potentialSavings: Long
)

data class DuplicateScanResult(
    val groups: List<DuplicateGroup>,
    val totalDuplicates: Int,
    val potentialSavings: Long
)

data class SimilarScanResult(
    val groups: List<SimilarPhotoGroup>,
    val totalSimilar: Int,
    val potentialSavings: Long
)

enum class CompressionQuality(@StringRes val labelRes: Int, val resolution: Int, val estimatedReductionPercent: Int) {
    HIGH(R.string.videos_quality_high, 1080, 30),
    MEDIUM(R.string.videos_quality_medium, 720, 50),
    LOW(R.string.videos_quality_low, 540, 70)
}
