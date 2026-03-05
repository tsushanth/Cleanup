package com.kreativekoala.cleanup.domain.service

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Port of iOS ArchiveUploadManager using WorkManager.
 *
 * Handles reliable background file uploads via presigned URLs.
 * Survives app restarts and process death.
 */
@HiltWorker
class ArchiveUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_FILE_URI = "file_uri"
        const val KEY_PRESIGNED_URL = "presigned_url"
        const val KEY_MIME_TYPE = "mime_type"
        const val KEY_ITEM_ID = "item_id"

        fun createWorkRequest(
            fileUri: String,
            presignedUrl: String,
            mimeType: String,
            itemId: String
        ): OneTimeWorkRequest {
            val inputData = workDataOf(
                KEY_FILE_URI to fileUri,
                KEY_PRESIGNED_URL to presignedUrl,
                KEY_MIME_TYPE to mimeType,
                KEY_ITEM_ID to itemId
            )

            return OneTimeWorkRequestBuilder<ArchiveUploadWorker>()
                .setInputData(inputData)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    java.util.concurrent.TimeUnit.MILLISECONDS
                )
                .build()
        }
    }

    override suspend fun doWork(): Result {
        val fileUriString = inputData.getString(KEY_FILE_URI) ?: return Result.failure()
        val presignedUrl = inputData.getString(KEY_PRESIGNED_URL) ?: return Result.failure()
        val mimeType = inputData.getString(KEY_MIME_TYPE) ?: "application/octet-stream"

        return try {
            // Copy URI content to temp file for reliable upload
            val tempFile = File(applicationContext.cacheDir, "upload_${System.currentTimeMillis()}")
            applicationContext.contentResolver.openInputStream(Uri.parse(fileUriString))?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            } ?: return Result.failure()

            try {
                // Upload to presigned URL
                val url = URL(presignedUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "PUT"
                conn.setRequestProperty("Content-Type", mimeType)
                conn.setRequestProperty("Content-Length", tempFile.length().toString())
                conn.doOutput = true

                FileInputStream(tempFile).use { input ->
                    conn.outputStream.use { output -> input.copyTo(output) }
                }

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    Result.success()
                } else {
                    Result.retry()
                }
            } finally {
                tempFile.delete()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
