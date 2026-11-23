package com.kreativekoala.cleanup.data.model

import android.content.Context
import androidx.annotation.StringRes
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kreativekoala.cleanup.R

enum class ArchiveTier(
    @StringRes val displayNameRes: Int,
    val icon: String,
    @StringRes val retrievalTimeRes: Int,
    val costIndicator: String
) {
    INSTANT(R.string.archive_tier_instant, "bolt", R.string.archive_retrieval_instant, "$$$"),
    FLEXIBLE(R.string.archive_tier_flexible, "clock", R.string.archive_retrieval_flexible, "$$"),
    DEEP(R.string.archive_tier_deep, "archive", R.string.archive_retrieval_deep, "$")
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

    /** Label as a string resource id; format arguments are in [labelArgs]. */
    @get:StringRes
    val labelRes: Int get() = when (this) {
        is Pending -> R.string.archive_status_pending
        is Uploading -> R.string.archive_status_uploading
        is Uploaded -> R.string.archive_status_uploaded
        is Archived -> R.string.archive_status_archived
        is Retrieving -> R.string.archive_status_retrieving
        is Available -> R.string.archive_status_ready
        is Failed -> R.string.archive_status_failed
    }

    val labelArgs: List<Any> get() = when (this) {
        is Uploading -> listOf((progress * 100).toInt())
        is Failed -> listOf(message)
        else -> emptyList()
    }

    fun displayText(context: Context): String = context.getString(labelRes, *labelArgs.toTypedArray())
}

enum class ArchivedFileType(val icon: String, @StringRes val displayNameRes: Int) {
    PHOTO("photo", R.string.archive_file_photo),
    VIDEO("video", R.string.archive_file_video),
    CONTACT("person", R.string.archive_file_contact),
    VAULT("lock", R.string.archive_file_vault)
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
    /** Plan size in GB; render it with R.string.size_unit_gb so the unit is localized. */
    val storageGb: Int,
    /** Fallback price shown only until Play returns the localized formatted price. */
    val monthlyPrice: String
) {
    TIER_5GB("cleanup_archive_5gb__199", 5_000_000_000, 5, "$1.99"),
    TIER_25GB("cleanup_archive_25gb_499", 25_000_000_000, 25, "$4.99"),
    TIER_100GB("cleanup_archive_100gb_1499", 100_000_000_000, 100, "$14.99");

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
