package com.kreativekoala.cleanup.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ArchiveTier(val displayName: String, val icon: String, val retrievalTime: String, val costIndicator: String) {
    INSTANT("Instant Access", "bolt", "Instant", "$$$"),
    FLEXIBLE("Flexible Retrieval", "clock", "3-5 hours", "$$"),
    DEEP("Deep Archive", "archive", "Up to 12 hours", "$")
}

sealed class ArchiveTransferStatus {
    data object Pending : ArchiveTransferStatus()
    data class Uploading(val progress: Double) : ArchiveTransferStatus()
    data object Uploaded : ArchiveTransferStatus()
    data object Archived : ArchiveTransferStatus()
    data object Retrieving : ArchiveTransferStatus()
    data class Available(val downloadUrl: String, val expiresAt: Long) : ArchiveTransferStatus()
    data class Failed(val message: String) : ArchiveTransferStatus()

    val isInProgress: Boolean get() = this is Uploading || this is Retrieving

    val displayText: String get() = when (this) {
        is Pending -> "Pending"
        is Uploading -> "Uploading ${(progress * 100).toInt()}%"
        is Uploaded -> "Uploaded"
        is Archived -> "Archived"
        is Retrieving -> "Retrieving..."
        is Available -> "Ready to Download"
        is Failed -> "Failed: $message"
    }
}

enum class ArchivedFileType(val icon: String, val displayName: String) {
    PHOTO("photo", "Photo"),
    VIDEO("video", "Video"),
    CONTACT("person", "Contact"),
    VAULT("lock", "Vault Item")
}

@Entity(tableName = "archived_items")
data class ArchivedItem(
    @PrimaryKey val id: String,
    val originalAssetId: String,
    val fileName: String,
    val fileType: String, // ArchivedFileType raw value
    val fileSize: Long,
    val storageTier: String, // ArchiveTier raw value
    val archivedDate: Long,
    val thumbnailPath: String? = null,
    val transferStatus: String = "archived",
    val creationDate: Long? = null,
    val pixelWidth: Int? = null,
    val pixelHeight: Int? = null,
    val duration: Double? = null
)

data class ArchiveQuota(
    val totalBytes: Long,
    val usedBytes: Long,
    val itemCount: Int,
    val subscriptionProductId: String? = null,
    val tier: String? = null
) {
    val remainingBytes: Long get() = maxOf(0, totalBytes - usedBytes)
    val usedPercentage: Double get() = if (totalBytes > 0) usedBytes.toDouble() / totalBytes else 0.0
}

enum class ArchiveSubscriptionTier(
    val productId: String,
    val storageLimitBytes: Long,
    val displayName: String,
    val monthlyPrice: String
) {
    TIER_5GB("cleanup_archive_5gb__199", 5_000_000_000, "5 GB", "$1.99"),
    TIER_25GB("cleanup_archive_25gb_499", 25_000_000_000, "25 GB", "$4.99"),
    TIER_100GB("cleanup_archive_100gb_1499", 100_000_000_000, "100 GB", "$14.99");

    companion object {
        fun fromProductId(productId: String?): ArchiveSubscriptionTier? =
            entries.find { it.productId == productId }
    }
}

// API response models
data class PresignedUploadResponse(val uploadURL: String, val s3Key: String, val expiresAt: String)
data class DownloadInitiationResponse(val status: String, val downloadURL: String?, val estimatedWaitMinutes: Int?)
data class DownloadStatusResponse(val status: String, val downloadURL: String?, val expiresAt: String?)
data class QuotaResponse(val totalBytes: Long, val usedBytes: Long, val itemCount: Int, val subscriptionProductId: String?, val tier: String?)
