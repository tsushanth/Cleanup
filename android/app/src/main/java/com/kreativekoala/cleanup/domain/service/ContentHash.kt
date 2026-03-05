package com.kreativekoala.cleanup.domain.service

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SHA-256 content hashing for detecting exact duplicate files.
 * Two files with identical SHA-256 hashes are byte-for-byte identical.
 */
@Singleton
class ContentHash @Inject constructor() {

    /**
     * Generate SHA-256 hash of file content.
     * @param contentResolver ContentResolver for reading file data
     * @param uri The URI of the file
     * @return Hex string of SHA-256 hash, or null if file can't be read
     */
    suspend fun generateHash(
        contentResolver: ContentResolver,
        uri: Uri
    ): String? = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)

            contentResolver.openInputStream(uri)?.use { inputStream ->
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            } ?: return@withContext null

            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}
