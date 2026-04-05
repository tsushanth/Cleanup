package com.kreativekoala.cleanup.domain.service

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.kreativekoala.cleanup.data.model.PhotoAsset
import com.kreativekoala.cleanup.data.model.VideoAsset
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles media access via SAF (Storage Access Framework) folder picker
 * and Android Photo Picker, replacing the removed READ_MEDIA_* permissions.
 */
@Singleton
class MediaAccessHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("media_access", Context.MODE_PRIVATE)
    private val idCounter = AtomicLong(1)

    // MARK: - Persistent SAF Folder Access

    fun getSavedFolderUri(): Uri? {
        val uriString = prefs.getString("folder_uri", null) ?: return null
        return Uri.parse(uriString)
    }

    fun saveFolderUri(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs.edit().putString("folder_uri", uri.toString()).apply()
    }

    fun hasFolderAccess(): Boolean {
        val uri = getSavedFolderUri() ?: return false
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }

    fun clearFolderAccess() {
        val uri = getSavedFolderUri() ?: return
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {}
        prefs.edit().remove("folder_uri").apply()
    }

    // MARK: - Load Photos from SAF Folder

    suspend fun loadPhotosFromFolder(treeUri: Uri): List<PhotoAsset> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<PhotoAsset>()
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        loadMediaRecursive(treeUri, treeDocId, photos, null, "image/")
        photos
    }

    suspend fun loadVideosFromFolder(treeUri: Uri): List<PhotoAsset> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<PhotoAsset>()
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        loadMediaRecursive(treeUri, treeDocId, photos, null, "video/")
        photos
    }

    suspend fun loadVideoAssetsFromFolder(treeUri: Uri): List<VideoAsset> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoAsset>()
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        loadMediaRecursive(treeUri, treeDocId, null, videos, "video/")
        videos
    }

    private fun loadMediaRecursive(
        treeUri: Uri,
        parentDocId: String,
        photos: MutableList<PhotoAsset>?,
        videos: MutableList<VideoAsset>?,
        mimePrefix: String
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val docIdCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val docId = cursor.getString(docIdCol) ?: continue
                val name = cursor.getString(nameCol) ?: continue
                val mimeType = cursor.getString(mimeCol) ?: ""
                val size = cursor.getLong(sizeCol)
                val lastModified = cursor.getLong(modifiedCol)

                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    loadMediaRecursive(treeUri, docId, photos, videos, mimePrefix)
                } else if (mimeType.startsWith(mimePrefix)) {
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    val id = idCounter.getAndIncrement()

                    if (photos != null && mimePrefix == "image/") {
                        photos.add(
                            PhotoAsset(
                                id = id,
                                uri = docUri,
                                dateAdded = lastModified / 1000,
                                dateTaken = lastModified,
                                fileSize = size,
                                width = 0,
                                height = 0,
                                displayName = name,
                                mimeType = mimeType,
                                relativePath = parentDocId
                            )
                        )
                    }
                    if (videos != null && mimePrefix == "video/") {
                        videos.add(
                            VideoAsset(
                                id = id,
                                uri = docUri,
                                dateAdded = lastModified / 1000,
                                fileSize = size,
                                duration = 0,
                                width = 0,
                                height = 0,
                                displayName = name,
                                mimeType = mimeType
                            )
                        )
                    }
                    if (photos != null && mimePrefix == "video/") {
                        photos.add(
                            PhotoAsset(
                                id = id,
                                uri = docUri,
                                dateAdded = lastModified / 1000,
                                dateTaken = lastModified,
                                fileSize = size,
                                width = 0,
                                height = 0,
                                displayName = name,
                                mimeType = mimeType,
                                relativePath = parentDocId
                            )
                        )
                    }
                }
            }
        }
    }

    // MARK: - Load from MediaStore (runtime permission based)

    suspend fun loadAllPhotosFromMediaStore(): List<PhotoAsset> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<PhotoAsset>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val projection = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE
        )
        val hasRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        if (hasRelativePath) projection.add(MediaStore.Images.Media.RELATIVE_PATH)

        context.contentResolver.query(
            collection, projection.toTypedArray(), null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val pathCol = if (hasRelativePath) cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH) else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                photos.add(
                    PhotoAsset(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        dateAdded = cursor.getLong(dateAddedCol),
                        dateTaken = cursor.getLong(dateTakenCol),
                        fileSize = cursor.getLong(sizeCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        displayName = cursor.getString(nameCol) ?: "",
                        mimeType = cursor.getString(mimeCol) ?: "image/jpeg",
                        relativePath = if (pathCol >= 0) cursor.getString(pathCol) ?: "" else ""
                    )
                )
            }
        }
        photos
    }

    suspend fun loadAllVideosFromMediaStore(): List<PhotoAsset> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<PhotoAsset>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE
        )
        val hasRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        if (hasRelativePath) projection.add(MediaStore.Video.Media.RELATIVE_PATH)

        context.contentResolver.query(
            collection, projection.toTypedArray(), null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val pathCol = if (hasRelativePath) cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH) else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                videos.add(
                    PhotoAsset(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        dateAdded = cursor.getLong(dateAddedCol),
                        dateTaken = 0L,
                        fileSize = cursor.getLong(sizeCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        displayName = cursor.getString(nameCol) ?: "",
                        mimeType = cursor.getString(mimeCol) ?: "video/mp4",
                        relativePath = if (pathCol >= 0) cursor.getString(pathCol) ?: "" else ""
                    )
                )
            }
        }
        videos
    }

    suspend fun loadAllVideoAssetsFromMediaStore(): List<VideoAsset> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoAsset>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE
        )

        context.contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                videos.add(
                    VideoAsset(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        dateAdded = cursor.getLong(dateAddedCol),
                        fileSize = cursor.getLong(sizeCol),
                        duration = cursor.getLong(durationCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        displayName = cursor.getString(nameCol) ?: "",
                        mimeType = cursor.getString(mimeCol) ?: "video/mp4"
                    )
                )
            }
        }
        videos
    }

    // MARK: - Load Photos from Photo Picker URIs

    suspend fun loadPhotosFromPickerUris(uris: List<Uri>): List<PhotoAsset> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri ->
            try {
                var name = ""
                var size = 0L
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(0) ?: ""
                        size = cursor.getLong(1)
                    }
                }

                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"

                PhotoAsset(
                    id = idCounter.getAndIncrement(),
                    uri = uri,
                    dateAdded = System.currentTimeMillis() / 1000,
                    dateTaken = 0L,
                    fileSize = size,
                    width = 0,
                    height = 0,
                    displayName = name,
                    mimeType = mimeType,
                    relativePath = ""
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun loadVideoAssetsFromPickerUris(uris: List<Uri>): List<VideoAsset> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri ->
            try {
                var name = ""
                var size = 0L
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(0) ?: ""
                        size = cursor.getLong(1)
                    }
                }

                val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"

                VideoAsset(
                    id = idCounter.getAndIncrement(),
                    uri = uri,
                    dateAdded = System.currentTimeMillis() / 1000,
                    fileSize = size,
                    duration = 0,
                    width = 0,
                    height = 0,
                    displayName = name,
                    mimeType = mimeType
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    // MARK: - Delete via SAF

    fun deleteDocument(uri: Uri): Boolean {
        return try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (_: Exception) {
            false
        }
    }
}
