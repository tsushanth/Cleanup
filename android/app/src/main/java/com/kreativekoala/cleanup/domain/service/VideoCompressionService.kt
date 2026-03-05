package com.kreativekoala.cleanup.domain.service

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.kreativekoala.cleanup.data.model.CompressionQuality
import com.kreativekoala.cleanup.data.model.VideoAsset
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class CompressionResult(
    val success: Boolean,
    val originalSize: Long,
    val compressedSize: Long,
    val savedBytes: Long,
    val outputUri: Uri?,
    val error: String? = null
) {
    val savingsPercentage: Double
        get() = if (originalSize > 0) savedBytes.toDouble() / originalSize * 100 else 0.0
}

/**
 * Port of iOS VideoCompressionService using Media3 Transformer.
 */
@Singleton
class VideoCompressionService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val contentResolver: ContentResolver get() = context.contentResolver

    /**
     * Estimate compressed file size.
     */
    fun estimateCompressedSize(originalSize: Long, quality: CompressionQuality): Long {
        val reductionFactor = when (quality) {
            CompressionQuality.HIGH -> 0.7
            CompressionQuality.MEDIUM -> 0.5
            CompressionQuality.LOW -> 0.3
        }
        return (originalSize * (1 - reductionFactor)).toLong()
    }

    fun estimateSavings(originalSize: Long, quality: CompressionQuality): Long {
        return originalSize - estimateCompressedSize(originalSize, quality)
    }

    /**
     * Compress a video using Media3 Transformer.
     *
     * @param video The video asset to compress
     * @param quality Compression quality preset
     * @param onProgress Callback for compression progress (0.0 - 1.0)
     * @return CompressionResult with success/failure and size info
     */
    suspend fun compressVideo(
        video: VideoAsset,
        quality: CompressionQuality,
        onProgress: (Float) -> Unit = {}
    ): CompressionResult = withContext(Dispatchers.Main) {
        val outputDir = File(context.cacheDir, "compressed_videos")
        outputDir.mkdirs()
        val outputFile = File(outputDir, "${UUID.randomUUID()}.mp4")
        val outputPath = outputFile.absolutePath

        try {
            val mediaItem = MediaItem.fromUri(video.uri)
            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(false)
                .build()

            val result = suspendCancellableCoroutine { continuation ->
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType("video/avc")
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(
                            composition: Composition,
                            exportResult: ExportResult
                        ) {
                            val compressedSize = outputFile.length()
                            val savedBytes = video.fileSize - compressedSize

                            continuation.resume(
                                CompressionResult(
                                    success = true,
                                    originalSize = video.fileSize,
                                    compressedSize = compressedSize,
                                    savedBytes = savedBytes,
                                    outputUri = Uri.fromFile(outputFile)
                                )
                            )
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            continuation.resume(
                                CompressionResult(
                                    success = false,
                                    originalSize = video.fileSize,
                                    compressedSize = 0,
                                    savedBytes = 0,
                                    outputUri = null,
                                    error = exportException.message
                                )
                            )
                        }
                    })
                    .build()

                transformer.start(editedMediaItem, outputPath)

                continuation.invokeOnCancellation {
                    transformer.cancel()
                    outputFile.delete()
                }
            }

            result
        } catch (e: Exception) {
            CompressionResult(
                success = false,
                originalSize = video.fileSize,
                compressedSize = 0,
                savedBytes = 0,
                outputUri = null,
                error = e.message
            )
        }
    }

    /**
     * Delete video assets via SAF or MediaStore.
     */
    suspend fun deleteVideos(videos: List<VideoAsset>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        for (video in videos) {
            try {
                if (DocumentsContract.deleteDocument(contentResolver, video.uri)) {
                    deleted++
                }
            } catch (_: Exception) {
                try {
                    contentResolver.delete(video.uri, null, null)
                    deleted++
                } catch (_: SecurityException) {}
            }
        }
        deleted
    }

    fun cleanupTempFiles() {
        val outputDir = File(context.cacheDir, "compressed_videos")
        outputDir.listFiles()?.forEach { it.delete() }
    }
}
