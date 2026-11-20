package com.tobi.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WatchHistoryEntity::class,
        WatchlistEntity::class,
        DownloadEntity::class,
        InstalledConnectorEntity::class,
        SearchHistoryEntity::class,
        CollectionEntity::class,
        CollectionItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun downloadDao(): DownloadDao
    abstract fun installedConnectorDao(): InstalledConnectorDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tobi.db"
                ).build().also { instance = it }
            }
        }
    }
}
