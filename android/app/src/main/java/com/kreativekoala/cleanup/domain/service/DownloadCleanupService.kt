package com.kreativekoala.cleanup.domain.service

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android-exclusive: Download Folder Cleanup Service.
 *
 * Scans MediaStore.Downloads for old, large, and duplicate files.
 * Groups by file type for easy cleanup.
 */
@Singleton
class DownloadCleanupService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class DownloadFile(
        val id: Long,
        val uri: Uri,
        val name: String,
        val size: Long,
        val mimeType: String,
        val dateAdded: Long,
        val category: DownloadCategory
    )

    enum class DownloadCategory(val displayName: String) {
        LARGE("Large Files (>50MB)"),
        OLD("Old Files (>30 days)"),
        DOCUMENTS("Documents"),
        IMAGES("Images"),
        VIDEOS("Videos"),
        ARCHIVES("Archives"),
        OTHER("Other")
    }

    data class DownloadScanResult(
        val largeFiles: List<DownloadFile> = emptyList(),
        val oldFiles: List<DownloadFile> = emptyList(),
        val allFiles: List<DownloadFile> = emptyList()
    ) {
        val totalSize: Long get() = allFiles.sumOf { it.size }
    }

    suspend fun scan(): DownloadScanResult = withContext(Dispatchers.IO) {
        val allFiles = mutableListOf<DownloadFile>()
        val thirtyDaysAgo = (System.currentTimeMillis() / 1000) - (30 * 24 * 60 * 60)
        val fiftyMB = 50L * 1024 * 1024

        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.MIME_TYPE,
            MediaStore.Downloads.DATE_ADDED
        )

        val uri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)

        context.contentResolver.query(
            uri, projection, null, null,
            "${MediaStore.Downloads.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Unknown"
                val size = cursor.getLong(sizeCol)
                val mimeType = cursor.getString(mimeCol) ?: ""
                val dateAdded = cursor.getLong(dateCol)

                val category = categorize(mimeType, size, dateAdded, thirtyDaysAgo, fiftyMB)

                allFiles.add(
                    DownloadFile(
                        id = id,
                        uri = Uri.withAppendedPath(uri, id.toString()),
                        name = name,
                        size = size,
                        mimeType = mimeType,
                        dateAdded = dateAdded * 1000,
                        category = category
                    )
                )
            }
        }

        val largeFiles = allFiles.filter { it.size >= fiftyMB }
        val oldFiles = allFiles.filter { it.dateAdded / 1000 < thirtyDaysAgo }

        DownloadScanResult(
            largeFiles = largeFiles,
            oldFiles = oldFiles,
            allFiles = allFiles
        )
    }

    suspend fun deleteFiles(files: List<DownloadFile>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        for (file in files) {
            try {
                if (context.contentResolver.delete(file.uri, null, null) > 0) {
                    deleted++
                }
            } catch (_: Exception) {}
        }
        deleted
    }

    private fun categorize(
        mimeType: String,
        size: Long,
        dateAdded: Long,
        thirtyDaysAgo: Long,
        fiftyMB: Long
    ): DownloadCategory {
        return when {
            size >= fiftyMB -> DownloadCategory.LARGE
            dateAdded < thirtyDaysAgo -> DownloadCategory.OLD
            mimeType.startsWith("image/") -> DownloadCategory.IMAGES
            mimeType.startsWith("video/") -> DownloadCategory.VIDEOS
            mimeType.contains("pdf") || mimeType.contains("document") || mimeType.contains("text") ->
                DownloadCategory.DOCUMENTS
            mimeType.contains("zip") || mimeType.contains("rar") || mimeType.contains("7z") ->
                DownloadCategory.ARCHIVES
            else -> DownloadCategory.OTHER
        }
    }
}
