package com.kreativekoala.cleanup.domain.service

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.kreativekoala.cleanup.data.model.DuplicateGroup
import com.kreativekoala.cleanup.data.model.DuplicateScanResult
import com.kreativekoala.cleanup.data.model.PhotoAsset
import com.kreativekoala.cleanup.data.model.ScanResult
import com.kreativekoala.cleanup.data.model.SimilarPhotoGroup
import com.kreativekoala.cleanup.data.model.SimilarScanResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides duplicate detection (SHA-256), similar photo detection (aHash),
 * screenshot detection, and large video scanning.
 *
 * All analysis methods accept a pre-loaded list of PhotoAsset objects,
 * which can come from the Photo Picker or SAF folder access.
 */
@Singleton
class PhotoAnalysisService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contentHash: ContentHash,
    private val perceptualHash: PerceptualHash
) {
    private val contentResolver: ContentResolver get() = context.contentResolver

    // MARK: - Find Duplicates

    /**
     * Find exact duplicate photos using SHA-256 content hashing.
     *
     * Algorithm:
     * 1. Group by file size (fast pre-filter - true duplicates have identical sizes)
     * 2. For same-size groups, compare SHA-256 content hashes
     * 3. Keep oldest (original), mark newer ones as duplicates
     */
    suspend fun findDuplicates(allPhotos: List<PhotoAsset>): DuplicateScanResult = withContext(Dispatchers.IO) {
        // Step 1: Group by file size (fast pre-filter)
        val sizeMap = mutableMapOf<Long, MutableList<PhotoAsset>>()
        for (photo in allPhotos) {
            if (photo.fileSize <= 1000) continue // Skip tiny files (thumbnails/corrupt)
            sizeMap.getOrPut(photo.fileSize) { mutableListOf() }.add(photo)
        }

        // Step 2: For same-size groups, compare content hashes
        val groups = mutableListOf<DuplicateGroup>()
        var totalDuplicates = 0
        var totalSavings = 0L
        val processedIds = mutableSetOf<Long>()

        for ((_, assets) in sizeMap) {
            if (assets.size < 2) continue

            // Hash each asset
            val hashMap = mutableMapOf<String, MutableList<PhotoAsset>>()
            for (asset in assets) {
                if (asset.id in processedIds) continue

                val hash = contentHash.generateHash(contentResolver, asset.uri) ?: continue
                hashMap.getOrPut(hash) { mutableListOf() }.add(asset)
            }

            // Create duplicate groups
            for ((hash, matchingAssets) in hashMap) {
                if (matchingAssets.size < 2) continue

                // Sort by date: oldest first (keep as original)
                val sorted = matchingAssets.sortedBy { it.dateTaken }
                val best = sorted.first()

                processedIds.addAll(sorted.map { it.id })

                val group = DuplicateGroup(
                    hash = hash,
                    photos = sorted,
                    bestPhoto = best
                )
                groups.add(group)
                totalDuplicates += group.duplicateCount
                totalSavings += group.potentialSavings
            }
        }

        DuplicateScanResult(
            groups = groups,
            totalDuplicates = totalDuplicates,
            potentialSavings = totalSavings
        )
    }

    // MARK: - Find Similar Photos

    /**
     * Find visually similar photos using Average Perceptual Hash (aHash).
     *
     * Algorithm:
     * 1. Sort photos by creation date ascending
     * 2. For each photo, look ahead within 15-second window
     * 3. Compare perceptual hashes (hamming distance <= 10/64 bits)
     * 4. Group similar photos, mark largest as "best"
     */
    suspend fun findSimilarPhotos(allPhotos: List<PhotoAsset>): SimilarScanResult = withContext(Dispatchers.IO) {
        val sortedPhotos = allPhotos.sortedBy { it.dateTaken }

        val groups = mutableListOf<SimilarPhotoGroup>()
        val processedIds = mutableSetOf<Long>()
        var totalSavings = 0L

        for (i in sortedPhotos.indices) {
            val photo = sortedPhotos[i]
            if (photo.id in processedIds) continue
            if (photo.dateTaken == 0L) continue

            val groupPhotos = mutableListOf(photo)
            processedIds.add(photo.id)

            // Look ahead within 15-second time window
            for (j in (i + 1) until sortedPhotos.size) {
                val other = sortedPhotos[j]
                if (other.dateTaken == 0L) continue

                val timeDiff = (other.dateTaken - photo.dateTaken) / 1000 // ms -> seconds
                if (timeDiff > 15) break // Photos sorted ascending, stop if beyond window

                if (other.id in processedIds) continue

                // Check visual similarity via perceptual hash
                val hash1 = perceptualHash.computeHash(contentResolver, photo.uri, photo.id)
                val hash2 = perceptualHash.computeHash(contentResolver, other.uri, other.id)

                if (hash1 != null && hash2 != null && perceptualHash.areSimilar(hash1, hash2)) {
                    groupPhotos.add(other)
                    processedIds.add(other.id)
                }
            }

            if (groupPhotos.size > 1) {
                // Best = largest file (usually highest quality)
                val sorted = groupPhotos.sortedByDescending { it.fileSize }
                val best = sorted.first()

                // Calculate similarity score
                val firstHash = perceptualHash.computeHash(contentResolver, best.uri, best.id)
                var totalSimilarity = 0.0
                var count = 0
                if (firstHash != null) {
                    for (p in sorted.drop(1)) {
                        val h = perceptualHash.computeHash(contentResolver, p.uri, p.id)
                        if (h != null) {
                            totalSimilarity += perceptualHash.similarityScore(firstHash, h)
                            count++
                        }
                    }
                }
                val score = if (count > 0) (totalSimilarity / count).toFloat() else 0.5f

                val group = SimilarPhotoGroup(
                    photos = groupPhotos,
                    bestPhoto = best,
                    similarityScore = score
                )
                groups.add(group)
                totalSavings += group.potentialSavings
            }
        }

        SimilarScanResult(
            groups = groups,
            totalSimilar = groups.sumOf { it.similarCount },
            potentialSavings = totalSavings
        )
    }

    // MARK: - Find Screenshots

    /**
     * Find screenshots by checking the display name or relative path for "Screenshot".
     */
    suspend fun findScreenshots(allPhotos: List<PhotoAsset>): ScanResult = withContext(Dispatchers.IO) {
        val screenshots = allPhotos.filter { photo ->
            val name = photo.displayName.lowercase()
            val path = photo.relativePath.lowercase()
            name.contains("screenshot") || path.contains("screenshot")
        }
        val totalBytes = screenshots.sumOf { it.fileSize }

        ScanResult(
            items = screenshots,
            totalCount = screenshots.size,
            potentialSavings = totalBytes
        )
    }

    // MARK: - Find Large Videos

    /**
     * Find videos larger than the minimum size threshold.
     * Default: 50 MB.
     */
    suspend fun findLargeVideos(allVideos: List<PhotoAsset>, minimumSize: Long = 50_000_000): ScanResult =
        withContext(Dispatchers.IO) {
            val largeVideos = allVideos
                .filter { it.fileSize >= minimumSize }
                .sortedByDescending { it.fileSize }
            val totalBytes = largeVideos.sumOf { it.fileSize }

            ScanResult(
                items = largeVideos,
                totalCount = largeVideos.size,
                potentialSavings = totalBytes
            )
        }

    // MARK: - Delete Assets

    /**
     * Delete assets via SAF (DocumentsContract).
     * Returns the number of successfully deleted files.
     */
    suspend fun deleteAssets(assets: List<PhotoAsset>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        for (asset in assets) {
            try {
                if (DocumentsContract.deleteDocument(contentResolver, asset.uri)) {
                    deleted++
                }
            } catch (_: Exception) {
                // URI may not be a SAF document — try MediaStore delete for picker URIs
                try {
                    contentResolver.delete(asset.uri, null, null)
                    deleted++
                } catch (_: Exception) {
                    // Ignore errors for individual files (SecurityException, UnsupportedOperationException, etc.)
                }
            }
        }
        deleted
    }

    /**
     * Create a delete request for the given photos (API 30+ system consent dialog).
     * Only works with MediaStore content URIs (not SAF URIs).
     * Returns null if URIs are SAF-based (use deleteAssets instead).
     */
    suspend fun createDeleteRequest(assets: List<PhotoAsset>): android.app.PendingIntent? =
        withContext(Dispatchers.IO) {
            val uris = assets.map { it.uri }

            // Check if these are MediaStore URIs (not SAF document URIs)
            val areMediaStoreUris = uris.all {
                it.authority == "media" || it.authority == "com.android.providers.media.documents"
            }

            if (!areMediaStoreUris) {
                // SAF URIs — delete directly
                deleteAssets(assets)
                return@withContext null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // API 30+: System consent dialog
                MediaStore.createDeleteRequest(contentResolver, uris)
            } else {
                // API 29-: Delete directly
                for (uri in uris) {
                    try {
                        contentResolver.delete(uri, null, null)
                    } catch (_: SecurityException) {}
                }
                null
            }
        }
}
