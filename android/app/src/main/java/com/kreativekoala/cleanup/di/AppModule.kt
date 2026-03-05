package com.kreativekoala.cleanup.di

import android.content.Context
import androidx.room.Room
import com.kreativekoala.cleanup.data.local.CleanupDatabase
import com.kreativekoala.cleanup.data.local.dao.ArchiveDao
import com.kreativekoala.cleanup.data.local.dao.VaultDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CleanupDatabase {
        return Room.databaseBuilder(
            context,
            CleanupDatabase::class.java,
            "cleanup_database"
        ).build()
    }

    @Provides
    fun provideVaultDao(database: CleanupDatabase): VaultDao = database.vaultDao()

    @Provides
    fun provideArchiveDao(database: CleanupDatabase): ArchiveDao = database.archiveDao()
}
