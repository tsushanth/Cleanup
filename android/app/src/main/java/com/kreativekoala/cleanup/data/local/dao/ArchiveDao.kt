package com.kreativekoala.cleanup.data.local.dao

import androidx.room.*
import com.kreativekoala.cleanup.data.model.ArchivedItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ArchiveDao {
    @Query("SELECT * FROM archived_items ORDER BY archivedDate DESC")
    fun getAllItems(): Flow<List<ArchivedItem>>

    @Query("SELECT * FROM archived_items WHERE fileType = :fileType ORDER BY archivedDate DESC")
    fun getItemsByType(fileType: String): Flow<List<ArchivedItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ArchivedItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ArchivedItem>)

    @Delete
    suspend fun deleteItem(item: ArchivedItem)

    @Query("DELETE FROM archived_items WHERE id = :itemId")
    suspend fun deleteById(itemId: String)

    @Query("DELETE FROM archived_items")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM archived_items")
    suspend fun getItemCount(): Int

    @Query("SELECT * FROM archived_items")
    suspend fun getAllItemsList(): List<ArchivedItem>
}
