package com.kreativekoala.cleanup.domain.service

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.kreativekoala.cleanup.data.local.dao.ArchiveDao
import com.kreativekoala.cleanup.data.model.ArchivedItem
import com.kreativekoala.cleanup.data.model.ArchiveQuota
import com.kreativekoala.cleanup.data.model.PresignedUploadResponse
import com.kreativekoala.cleanup.data.remote.ArchiveApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Port of iOS ArchiveService.
 *
 * Orchestrates archive operations: upload, retrieval, download, quota management.
 * Uses Room for local metadata cache and Retrofit for API calls.
 */
@Singleton
class ArchiveService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val archiveDao: ArchiveDao,
    private val archiveApi: ArchiveApiService,
    private val authService: ArchiveAuthService
) {
    // MARK: - Local Items (Room DB)

    fun getAllItems(): Flow<List<ArchivedItem>> = archiveDao.getAllItems()

    fun getItemsByType(fileType: String): Flow<List<ArchivedItem>> =
        archiveDao.getItemsByType(fileType)

    // MARK: - Archive Photo/Video

    suspend fun archiveMedia(
        uri: Uri,
        fileName: String,
        fileType: String,
        fileSize: Long,
        tier: String = "INSTANT",
        deleteAfterArchive: Boolean = true
    ): ArchivedItem = withContext(Dispatchers.IO) {
        val itemId = UUID.randomUUID().toString()

        // Copy to temp file
        val tempFile = File(context.cacheDir, "archive_$itemId")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        }

        // Get mime type
        val mimeType = context.contentResolver.getType(uri) ?: when (fileType) {
            "VIDEO" -> "video/mp4"
            else -> "image/jpeg"
        }

        // Request presigned upload URL
        val presignBody = mapOf<String, Any>(
            "itemId" to itemId,
            "filename" to fileName,
            "fileSize" to fileSize,
            "mimeType" to mimeType,
            "storageTier" to tier
        )
        val presignResponse = archiveApi.requestUploadUrl(presignBody)

        // Upload file to presigned URL
        uploadFile(tempFile, presignResponse.uploadURL, mimeType)

        // Generate thumbnail
        val thumbnailPath = generateThumbnail(uri, itemId, fileType)

        // Confirm upload with backend
        val confirmBody = mutableMapOf<String, Any>(
            "itemId" to itemId,
            "fileName" to fileName,
            "fileType" to fileType,
            "fileSize" to fileSize,
            "storageTier" to tier
        )
        archiveApi.confirmUpload(confirmBody)

        // Create local record
        val archivedItem = ArchivedItem(
            id = itemId,
            originalAssetId = uri.toString(),
            fileName = fileName,
            fileType = fileType,
            fileSize = fileSize,
            storageTier = tier,
            archivedDate = System.currentTimeMillis(),
            thumbnailPath = thumbnailPath,
            transferStatus = "archived"
        )

        archiveDao.insertItem(archivedItem)

        // Cleanup temp
        tempFile.delete()

        // Delete original if requested
        if (deleteAfterArchive) {
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {}
        }

        archivedItem
    }

    // MARK: - Retrieval

    suspend fun initiateRetrieval(itemId: String) = withContext(Dispatchers.IO) {
        val response = archiveApi.initiateDownload(mapOf("itemId" to itemId))

        if (response.status == "available" && response.downloadURL != null) {
            // Update local status
            // Item is ready for download
        } else {
            // Update to "retrieving" status
        }

        response
    }

    suspend fun downloadAndRestore(itemId: String, downloadUrl: String) = withContext(Dispatchers.IO) {
        // Download file
        val url = URL(downloadUrl)
        val conn = url.openConnection() as HttpURLConnection
        val tempFile = File(context.cacheDir, "download_$itemId")

        conn.inputStream.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        }

        // Find archived item
        val items = archiveDao.getAllItemsList()
        val item = items.find { it.id == itemId }
            ?: throw ArchiveAuthService.ArchiveError.InvalidResponse

        try {
            // Restore to gallery
            restoreToGallery(item, tempFile)
        } finally {
            tempFile.delete()
        }
    }

    // MARK: - Delete

    suspend fun deleteArchivedItem(itemId: String) = withContext(Dispatchers.IO) {
        archiveDao.deleteById(itemId)
        try {
            archiveApi.deleteItem(itemId)
        } catch (_: Exception) {
            // Fire and forget - local cache already updated
        }
    }

    // MARK: - Quota

    suspend fun getQuota(): ArchiveQuota = withContext(Dispatchers.IO) {
        val response = archiveApi.getQuota()
        ArchiveQuota(
            totalBytes = response.totalBytes,
            usedBytes = response.usedBytes,
            itemCount = response.itemCount,
            subscriptionProductId = response.subscriptionProductId,
            tier = response.tier
        )
    }

    // MARK: - Sync

    suspend fun syncItems() = withContext(Dispatchers.IO) {
        val response = archiveApi.listItems()

        for (remoteItem in response.items) {
            val archivedItem = ArchivedItem(
                id = remoteItem.id,
                originalAssetId = "",
                fileName = remoteItem.fileName,
                fileType = remoteItem.fileType,
                fileSize = remoteItem.fileSize,
                storageTier = remoteItem.storageTier,
                archivedDate = System.currentTimeMillis(),
                transferStatus = remoteItem.transferStatus ?: "archived",
                pixelWidth = remoteItem.pixelWidth,
                pixelHeight = remoteItem.pixelHeight,
                duration = remoteItem.duration
            )
            archiveDao.insertItem(archivedItem)
        }
    }

    // MARK: - Private Helpers

    private fun uploadFile(file: File, presignedUrl: String, mimeType: String) {
        val url = URL(presignedUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.setRequestProperty("Content-Type", mimeType)
        conn.doOutput = true

        FileInputStream(file).use { input ->
            conn.outputStream.use { output -> input.copyTo(output) }
        }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            throw ArchiveAuthService.ArchiveError.UploadFailed("Upload HTTP $responseCode")
        }
    }

    private fun generateThumbnail(uri: Uri, itemId: String, fileType: String): String? {
        return try {
            val bitmap = when (fileType) {
                "VIDEO" -> {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        retriever.getFrameAtTime(0)
                    } finally {
                        retriever.release()
                    }
                }
                else -> {
                    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    }
                }
            }

            bitmap?.let { bmp ->
                val scaled = Bitmap.createScaledBitmap(bmp, 200, 200, true)
                val thumbDir = File(context.filesDir, ".archive_thumbs").also { it.mkdirs() }
                val thumbFile = File(thumbDir, "$itemId.jpg")
                FileOutputStream(thumbFile).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 50, out)
                }
                if (scaled !== bmp) scaled.recycle()
                bmp.recycle()
                thumbFile.absolutePath
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun restoreToGallery(item: ArchivedItem, file: File) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(item))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    if (item.fileType == "VIDEO") Environment.DIRECTORY_MOVIES
                    else Environment.DIRECTORY_PICTURES
                )
            }
        }

        val collection = if (item.fileType == "VIDEO") {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

        val insertUri = context.contentResolver.insert(collection, contentValues)
        insertUri?.let { uri ->
            context.contentResolver.openOutputStream(uri)?.use { output ->
                FileInputStream(file).use { input -> input.copyTo(output) }
            }
        }
    }

    private fun getMimeType(item: ArchivedItem): String {
        val ext = item.fileName.substringAfterLast('.', "").lowercase()
        return when {
            item.fileType == "VIDEO" -> when (ext) {
                "mp4" -> "video/mp4"
                "mov" -> "video/quicktime"
                else -> "video/mp4"
            }
            else -> when (ext) {
                "png" -> "image/png"
                "heic", "heif" -> "image/heif"
                else -> "image/jpeg"
            }
        }
    }
}
