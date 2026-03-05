package com.kreativekoala.cleanup.domain.service

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import com.kreativekoala.cleanup.data.local.dao.VaultDao
import com.kreativekoala.cleanup.data.model.VaultFileType
import com.kreativekoala.cleanup.data.model.VaultItem
import com.kreativekoala.cleanup.data.model.VaultSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Port of iOS VaultService.
 *
 * Manages encrypted file storage for the Secret Space feature.
 * Files are encrypted with AES-GCM using keys stored in AndroidKeyStore.
 * Metadata is stored in Room database.
 */
@Singleton
class VaultService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultDao: VaultDao
) {
    companion object {
        private const val KEYSTORE_ALIAS = "cleanup_vault_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        private const val VAULT_DIR = ".vault"
        private const val THUMBNAILS_DIR = ".vault_thumbs"
        private const val SETTINGS_PREFS = "vault_settings_prefs"
        private const val KEY_PIN = "pin"
        private const val KEY_FAKE_PIN = "fake_pin"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_AUTO_LOCK_INTERVAL = "auto_lock_interval"
    }

    private val vaultDir: File by lazy {
        File(context.filesDir, VAULT_DIR).also { it.mkdirs() }
    }

    private val thumbnailDir: File by lazy {
        File(context.filesDir, THUMBNAILS_DIR).also { it.mkdirs() }
    }

    private val settingsPrefs by lazy {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    }

    init {
        ensureKeyExists()
    }

    // MARK: - Items (Room DB)

    fun getAllItems(): Flow<List<VaultItem>> = vaultDao.getAllItems()

    suspend fun getItemCount(): Int = vaultDao.getItemCount()

    // MARK: - Settings

    fun getSettings(): VaultSettings {
        return VaultSettings(
            pin = settingsPrefs.getString(KEY_PIN, null),
            fakePin = settingsPrefs.getString(KEY_FAKE_PIN, null),
            isBiometricEnabled = settingsPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false),
            autoLockIntervalSeconds = settingsPrefs.getInt(KEY_AUTO_LOCK_INTERVAL, 60)
        )
    }

    fun hasPin(): Boolean {
        val pin = settingsPrefs.getString(KEY_PIN, null)
        return !pin.isNullOrEmpty()
    }

    fun verifyPin(pin: String): Boolean {
        val storedPin = settingsPrefs.getString(KEY_PIN, null) ?: return false
        return storedPin == pin
    }

    fun isFakePin(pin: String): Boolean {
        val fakePin = settingsPrefs.getString(KEY_FAKE_PIN, null) ?: return false
        return fakePin == pin
    }

    fun setPin(pin: String) {
        settingsPrefs.edit().putString(KEY_PIN, pin).apply()
    }

    fun setFakePin(fakePin: String?) {
        settingsPrefs.edit().putString(KEY_FAKE_PIN, fakePin).apply()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        settingsPrefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun canUseBiometrics(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    // MARK: - Add to Vault

    /**
     * Add a media file to the vault.
     * Copies the file to encrypted vault storage, generates a thumbnail, and saves metadata.
     *
     * @param uri The content URI of the media to add
     * @param fileName The display name of the file
     * @param fileType Photo or video
     * @param fileSize Size in bytes
     * @param deleteOriginal Whether to delete the original after copying
     * @return The created VaultItem
     */
    suspend fun addToVault(
        uri: Uri,
        fileName: String,
        fileType: VaultFileType,
        fileSize: Long,
        deleteOriginal: Boolean = false
    ): VaultItem = withContext(Dispatchers.IO) {
        val itemId = UUID.randomUUID().toString()

        // Encrypt and save file
        val encryptedFile = File(vaultDir, itemId)
        encryptFile(uri, encryptedFile)

        // Generate thumbnail
        val thumbnailPath = generateThumbnail(uri, itemId, fileType)

        val item = VaultItem(
            id = itemId,
            fileName = fileName,
            fileType = fileType,
            fileSize = fileSize,
            addedDate = System.currentTimeMillis(),
            thumbnailPath = thumbnailPath,
            encryptedPath = encryptedFile.absolutePath
        )

        vaultDao.insertItem(item)

        // Delete original if requested
        if (deleteOriginal) {
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {
                // May fail without permission, that's OK
            }
        }

        item
    }

    // MARK: - Remove from Vault

    /**
     * Remove an item from the vault.
     * Optionally restores the file to the device gallery.
     */
    suspend fun removeFromVault(
        item: VaultItem,
        restoreToPhotos: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (restoreToPhotos) {
            restoreToGallery(item)
        }

        // Delete encrypted file
        File(item.encryptedPath).delete()

        // Delete thumbnail
        item.thumbnailPath?.let { File(it).delete() }

        // Remove from database
        vaultDao.deleteById(item.id)
    }

    // MARK: - Get Decrypted File

    /**
     * Decrypt a vault item to a temporary file for viewing.
     * The caller should delete the temp file when done.
     */
    suspend fun getDecryptedFile(item: VaultItem): File = withContext(Dispatchers.IO) {
        val encryptedFile = File(item.encryptedPath)
        val extension = item.fileName.substringAfterLast('.', "")
        val tempFile = File(context.cacheDir, "vault_temp_${item.id}.$extension")

        decryptFile(encryptedFile, tempFile)
        tempFile
    }

    /**
     * Clean up temp decrypted files.
     */
    fun cleanupTempFiles() {
        context.cacheDir.listFiles()?.filter { it.name.startsWith("vault_temp_") }?.forEach {
            it.delete()
        }
    }

    // MARK: - Encryption (AES-GCM via AndroidKeyStore)

    private fun ensureKeyExists() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val keyGen = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            keyGen.init(
                KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            keyGen.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
    }

    private fun encryptFile(sourceUri: Uri, destination: File) {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())

        val iv = cipher.iv

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destination).use { output ->
                // Write IV first (12 bytes)
                output.write(iv)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    val encrypted = cipher.update(buffer, 0, bytesRead)
                    if (encrypted != null) output.write(encrypted)
                }
                val finalBlock = cipher.doFinal()
                if (finalBlock != null) output.write(finalBlock)
            }
        }
    }

    private fun decryptFile(source: File, destination: File) {
        FileInputStream(source).use { input ->
            // Read IV (12 bytes)
            val iv = ByteArray(GCM_IV_LENGTH)
            input.read(iv)

            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))

            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    val decrypted = cipher.update(buffer, 0, bytesRead)
                    if (decrypted != null) output.write(decrypted)
                }
                val finalBlock = cipher.doFinal()
                if (finalBlock != null) output.write(finalBlock)
            }
        }
    }

    // MARK: - Thumbnail Generation

    private fun generateThumbnail(uri: Uri, itemId: String, fileType: VaultFileType): String? {
        return try {
            val bitmap = when (fileType) {
                VaultFileType.PHOTO -> {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    }
                    // Calculate sample size for ~200x200
                    val sampleSize = maxOf(
                        (options.outWidth / 200).coerceAtLeast(1),
                        (options.outHeight / 200).coerceAtLeast(1)
                    )
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, decodeOptions)
                    }
                }
                VaultFileType.VIDEO -> {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        retriever.getFrameAtTime(0)
                    } finally {
                        retriever.release()
                    }
                }
                VaultFileType.DOCUMENT -> null
            }

            bitmap?.let { bmp ->
                val scaled = Bitmap.createScaledBitmap(bmp, 200, 200, true)
                val thumbFile = File(thumbnailDir, "${itemId}.jpg")
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

    // MARK: - Restore to Gallery

    private fun restoreToGallery(item: VaultItem) {
        val tempFile = File(context.cacheDir, "restore_${item.id}")
        decryptFile(File(item.encryptedPath), tempFile)

        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(item))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        if (item.fileType == VaultFileType.VIDEO)
                            Environment.DIRECTORY_MOVIES
                        else
                            Environment.DIRECTORY_PICTURES
                    )
                }
            }

            val collection = if (item.fileType == VaultFileType.VIDEO) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }

            val insertUri = context.contentResolver.insert(collection, contentValues)
            insertUri?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(tempFile).use { input ->
                        input.copyTo(output)
                    }
                }
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun getMimeType(item: VaultItem): String {
        val ext = item.fileName.substringAfterLast('.', "").lowercase()
        return when {
            item.fileType == VaultFileType.VIDEO -> when (ext) {
                "mp4" -> "video/mp4"
                "mov" -> "video/quicktime"
                "avi" -> "video/x-msvideo"
                else -> "video/mp4"
            }
            else -> when (ext) {
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "heic", "heif" -> "image/heif"
                else -> "image/jpeg"
            }
        }
    }
}
