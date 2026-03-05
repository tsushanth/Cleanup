package com.kreativekoala.cleanup.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kreativekoala.cleanup.data.local.dao.VaultDao
import com.kreativekoala.cleanup.data.local.dao.ArchiveDao
import com.kreativekoala.cleanup.data.model.VaultItem
import com.kreativekoala.cleanup.data.model.ArchivedItem

@Database(
    entities = [VaultItem::class, ArchivedItem::class],
    version = 1,
    exportSchema = false
)
abstract class CleanupDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
    abstract fun archiveDao(): ArchiveDao
}
