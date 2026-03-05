package com.kreativekoala.cleanup.domain.service

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Average Perceptual Hash (aHash) implementation for detecting visually similar images.
 *
 * Algorithm:
 * 1. Resize image to 8x8 pixels
 * 2. Convert to grayscale
 * 3. Calculate average brightness
 * 4. Build 64-bit hash: 1 if pixel > average, 0 otherwise
 * 5. Compare hashes using hamming distance (XOR + popcount)
 *
 * Threshold: hamming distance <= 10 out of 64 bits = visually similar
 */
@Singleton
class PerceptualHash @Inject constructor() {

    private val hashCache = ConcurrentHashMap<Long, ULong>()

    /**
     * Compute the average perceptual hash for an image.
     * @param contentResolver ContentResolver for reading image data
     * @param uri The URI of the image
     * @param id The unique ID for caching
     * @return 64-bit hash, or null if image can't be processed
     */
    suspend fun computeHash(
        contentResolver: ContentResolver,
        uri: Uri,
        id: Long
    ): ULong? = withContext(Dispatchers.IO) {
        hashCache[id]?.let { return@withContext it }

        try {
            // Decode a small version of the image
            val options = BitmapFactory.Options().apply {
                inSampleSize = 8 // Downsample first for speed
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null
            val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            bitmap ?: return@withContext null

            // Resize to 8x8
            val scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
            if (scaled != bitmap) bitmap.recycle()

            // Convert to grayscale
            val grayscale = toGrayscale(scaled)
            if (grayscale != scaled) scaled.recycle()

            // Get pixel brightness values
            val pixels = IntArray(64)
            grayscale.getPixels(pixels, 0, 8, 0, 0, 8, 8)
            grayscale.recycle()

            // Calculate average brightness (use red channel since it's grayscale)
            var total = 0L
            val brightness = IntArray(64)
            for (i in 0 until 64) {
                val b = pixels[i] and 0xFF // Blue channel (same as R and G in grayscale)
                brightness[i] = b
                total += b
            }
            val average = (total / 64).toInt()

            // Build 64-bit hash
            var hashValue: ULong = 0u
            for (i in 0 until 64) {
                if (brightness[i] > average) {
                    hashValue = hashValue or (1uL shl i)
                }
            }

            hashCache[id] = hashValue
            hashValue
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Calculate hamming distance between two hashes (number of differing bits).
     */
    fun hammingDistance(hash1: ULong, hash2: ULong): Int {
        return (hash1 xor hash2).countOneBits()
    }

    /**
     * Check if two hashes are visually similar.
     * Threshold: <= 10 out of 64 bits different.
     */
    fun areSimilar(hash1: ULong, hash2: ULong): Boolean {
        return hammingDistance(hash1, hash2) <= SIMILARITY_THRESHOLD
    }

    /**
     * Calculate similarity score (0.0 to 1.0) from hamming distance.
     */
    fun similarityScore(hash1: ULong, hash2: ULong): Double {
        val distance = hammingDistance(hash1, hash2)
        return 1.0 - (distance.toDouble() / 64.0)
    }

    fun clearCache() {
        hashCache.clear()
    }

    private fun toGrayscale(source: Bitmap): Bitmap {
        val grayscale = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayscale)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return grayscale
    }

    companion object {
        const val SIMILARITY_THRESHOLD = 10
    }
}
