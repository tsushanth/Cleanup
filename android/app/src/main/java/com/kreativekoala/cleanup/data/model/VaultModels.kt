package com.kreativekoala.cleanup.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultItem(
    @PrimaryKey val id: String,
    val fileName: String,
    val fileType: VaultFileType,
    val fileSize: Long,
    val addedDate: Long,
    val thumbnailPath: String? = null,
    val encryptedPath: String
)

enum class VaultFileType { PHOTO, VIDEO, DOCUMENT }

data class VaultSettings(
    val pin: String? = null,
    val fakePin: String? = null,
    val isBiometricEnabled: Boolean = false,
    val autoLockIntervalSeconds: Int = 60
)
