package com.kreativekoala.cleanup.data.local.dao

import androidx.room.*
import com.kreativekoala.cleanup.data.model.VaultItem
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_items ORDER BY addedDate DESC")
    fun getAllItems(): Flow<List<VaultItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: VaultItem)

    @Delete
    suspend fun deleteItem(item: VaultItem)

    @Query("DELETE FROM vault_items WHERE id = :itemId")
    suspend fun deleteById(itemId: String)

    @Query("SELECT COUNT(*) FROM vault_items")
    suspend fun getItemCount(): Int
}
