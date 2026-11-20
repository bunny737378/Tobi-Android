package com.tobi.app.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "watch_history",
    indices = [Index(value = ["connectorId", "uid", "unitId"])]
)
data class WatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val connectorId: String,
    val uid: String,
    val title: String,
    val kind: String? = null,
    val cover: String? = null,
    val banner: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val lastWatchedAt: Long = 0,
    val groupId: String? = null,
    val unitId: String? = null,
    val episodeLabel: String? = null,
    val targetId: String? = null,
    val carryJson: String = "{}",
    val isLive: Boolean = false
)

@Entity(
    tableName = "watchlist",
    indices = [Index(value = ["connectorId", "uid"], unique = true)]
)
data class WatchlistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val connectorId: String,
    val uid: String,
    val title: String,
    val cover: String? = null,
    val kind: String? = null,
    val addedAt: Long = 0
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val connectorId: String,
    val title: String,
    val fileName: String,
    val remoteUri: String,
    val localPath: String? = null,
    val status: String = "QUEUED",
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val speedBps: Long = 0,
    val headersJson: String = "{}",
    val netJson: String? = null,
    val isHls: Boolean = false,
    val segmentTotal: Int = 0,
    val segmentDone: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Long = 0,
    val completedAt: Long? = null
)

@Entity(tableName = "installed_connectors")
data class InstalledConnectorEntity(
    @PrimaryKey val connectorId: String,
    val displayName: String,
    val version: String,
    val scriptPath: String,
    val manifestJson: String,
    val enabled: Boolean = true,
    val sourceUrl: String? = null,
    val installedAt: Long = 0,
    val updatedAt: Long = 0
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val searchedAt: Long = 0
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = 0
)

@Entity(
    tableName = "collection_items",
    primaryKeys = ["collectionId", "connectorId", "uid"]
)
data class CollectionItemEntity(
    val collectionId: Long,
    val connectorId: String,
    val uid: String,
    val title: String,
    val cover: String? = null,
    val addedAt: Long = 0
)
