package com.tobi.app.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY lastWatchedAt DESC")
    fun observeAll(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE connectorId = :connectorId AND uid = :uid ORDER BY lastWatchedAt DESC")
    fun observeByUid(connectorId: String, uid: String): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE connectorId = :connectorId AND uid = :uid AND unitId = :unitId LIMIT 1")
    suspend fun getByUnit(connectorId: String, uid: String, unitId: String?): WatchHistoryEntity?

    @Query("SELECT * FROM watch_history WHERE connectorId = :connectorId AND uid = :uid AND unitId IS NULL LIMIT 1")
    suspend fun getByMovie(connectorId: String, uid: String): WatchHistoryEntity?

    @Query("SELECT * FROM watch_history WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WatchHistoryEntity): Long

    @Update
    suspend fun update(entity: WatchHistoryEntity)

    @Delete
    suspend fun delete(entity: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist WHERE connectorId = :connectorId AND uid = :uid LIMIT 1")
    suspend fun getByUid(connectorId: String, uid: String): WatchlistEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE connectorId = :connectorId AND uid = :uid)")
    fun observeExists(connectorId: String, uid: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WatchlistEntity): Long

    @Query("DELETE FROM watchlist WHERE connectorId = :connectorId AND uid = :uid")
    suspend fun deleteByUid(connectorId: String, uid: String)

    @Query("DELETE FROM watchlist WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status IN ('DOWNLOADING', 'QUEUED') ORDER BY createdAt ASC")
    suspend fun getActive(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY createdAt ASC")
    suspend fun getQueued(): List<DownloadEntity>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'DOWNLOADING'")
    suspend fun countDownloading(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity): Long

    @Update
    suspend fun update(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, speedBps = :speedBps, segmentTotal = :segmentTotal, segmentDone = :segmentDone, localPath = :localPath, errorMessage = :errorMessage, completedAt = :completedAt WHERE id = :id")
    suspend fun updateProgress(
        id: Long,
        status: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBps: Long,
        segmentTotal: Int,
        segmentDone: Int,
        localPath: String?,
        errorMessage: String?,
        completedAt: Long?
    )

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface InstalledConnectorDao {
    @Query("SELECT * FROM installed_connectors ORDER BY displayName ASC")
    fun observeAll(): Flow<List<InstalledConnectorEntity>>

    @Query("SELECT * FROM installed_connectors WHERE connectorId = :connectorId LIMIT 1")
    suspend fun getById(connectorId: String): InstalledConnectorEntity?

    @Query("SELECT * FROM installed_connectors WHERE enabled = 1 ORDER BY displayName ASC")
    suspend fun getEnabled(): List<InstalledConnectorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: InstalledConnectorEntity)

    @Query("DELETE FROM installed_connectors WHERE connectorId = :connectorId")
    suspend fun delete(connectorId: String)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 20")
    fun observeRecent(): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteByQuery(query: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SearchHistoryEntity): Long

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY createdAt DESC")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :id LIMIT 1")
    suspend fun getCollection(id: Long): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(entity: CollectionEntity): Long

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollection(id: Long)

    @Query("SELECT * FROM collection_items WHERE collectionId = :collectionId ORDER BY addedAt DESC")
    fun observeItems(collectionId: Long): Flow<List<CollectionItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(entity: CollectionItemEntity)

    @Query("DELETE FROM collection_items WHERE collectionId = :collectionId AND connectorId = :connectorId AND uid = :uid")
    suspend fun deleteItem(collectionId: Long, connectorId: String, uid: String)

    @Query("DELETE FROM collection_items WHERE collectionId = :collectionId")
    suspend fun deleteItemsOf(collectionId: Long)
}
