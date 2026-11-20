package com.tobi.app.core.data

import com.tobi.app.core.database.AppDatabase
import com.tobi.app.core.database.CollectionEntity
import com.tobi.app.core.database.CollectionItemEntity
import com.tobi.app.core.database.SearchHistoryEntity
import com.tobi.app.core.database.WatchHistoryEntity
import com.tobi.app.core.database.WatchlistEntity
import com.tobi.app.core.network.UserPreferences
import com.tobi.app.core.protocol.ConnectorJson
import com.tobi.app.core.runtime.ConnectorManager
import com.tobi.app.core.runtime.InstalledConnector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class HistoryRepository(private val database: AppDatabase) {
    fun observeContinueWatching(): Flow<List<WatchHistoryEntity>> {
        return database.watchHistoryDao().observeAll()
    }

    fun observeByUid(connectorId: String, uid: String): Flow<List<WatchHistoryEntity>> {
        return database.watchHistoryDao().observeByUid(connectorId, uid)
    }

    suspend fun recordProgress(
        connectorId: String,
        uid: String,
        title: String,
        kind: String?,
        cover: String?,
        banner: String?,
        positionMs: Long,
        durationMs: Long,
        groupId: String?,
        unitId: String?,
        episodeLabel: String?,
        targetId: String?,
        carry: Map<String, String>,
        isLive: Boolean
    ) = withContext(Dispatchers.IO) {
        val carryJson = ConnectorJson.encodeToString(
            kotlinx.serialization.builtins.serializer<Map<String, String>>(),
            carry
        )
        val existing = if (unitId != null) {
            database.watchHistoryDao().getByUnit(connectorId, uid, unitId)
        } else {
            database.watchHistoryDao().getByMovie(connectorId, uid)
        }
        if (existing == null) {
            database.watchHistoryDao().insert(
                WatchHistoryEntity(
                    connectorId = connectorId,
                    uid = uid,
                    title = title,
                    kind = kind,
                    cover = cover,
                    banner = banner,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    lastWatchedAt = System.currentTimeMillis(),
                    groupId = groupId,
                    unitId = unitId,
                    episodeLabel = episodeLabel,
                    targetId = targetId,
                    carryJson = carryJson,
                    isLive = isLive
                )
            )
        } else {
            database.watchHistoryDao().update(
                existing.copy(
                    title = title,
                    kind = kind,
                    cover = cover,
                    banner = banner,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    lastWatchedAt = System.currentTimeMillis(),
                    groupId = groupId,
                    episodeLabel = episodeLabel,
                    targetId = targetId,
                    carryJson = carryJson,
                    isLive = isLive
                )
            )
        }
    }

    suspend fun getProgress(connectorId: String, uid: String, unitId: String?): WatchHistoryEntity? {
        return withContext(Dispatchers.IO) {
            if (unitId != null) {
                database.watchHistoryDao().getByUnit(connectorId, uid, unitId)
            } else {
                database.watchHistoryDao().getByMovie(connectorId, uid)
            }
        }
    }

    suspend fun remove(id: Long) = withContext(Dispatchers.IO) {
        database.watchHistoryDao().deleteById(id)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        database.watchHistoryDao().clearAll()
    }
}

class WatchlistRepository(private val database: AppDatabase) {
    fun observeWatchlist(): Flow<List<WatchlistEntity>> {
        return database.watchlistDao().observeAll()
    }

    fun observeExists(connectorId: String, uid: String): Flow<Boolean> {
        return database.watchlistDao().observeExists(connectorId, uid)
    }

    suspend fun toggle(connectorId: String, uid: String, title: String, cover: String?, kind: String?) = withContext(Dispatchers.IO) {
        val existing = database.watchlistDao().getByUid(connectorId, uid)
        if (existing == null) {
            database.watchlistDao().insert(
                WatchlistEntity(
                    connectorId = connectorId,
                    uid = uid,
                    title = title,
                    cover = cover,
                    kind = kind,
                    addedAt = System.currentTimeMillis()
                )
            )
        } else {
            database.watchlistDao().deleteByUid(connectorId, uid)
        }
    }

    suspend fun remove(connectorId: String, uid: String) = withContext(Dispatchers.IO) {
        database.watchlistDao().deleteByUid(connectorId, uid)
    }

    suspend fun removeById(id: Long) = withContext(Dispatchers.IO) {
        database.watchlistDao().deleteById(id)
    }
}

class ConnectorRepository(
    private val manager: ConnectorManager,
    private val prefs: UserPreferences
) {
    fun observeInstalled(): Flow<List<InstalledConnector>> = manager.observeInstalled()

    val activeConnector: Flow<InstalledConnector?> = combine(
        manager.observeInstalled(),
        prefs.state
    ) { installed, state ->
        val activeId = state.activeConnectorId
        installed.firstOrNull { it.connectorId == activeId && it.enabled }
            ?: installed.firstOrNull { it.enabled }
    }

    fun setActive(connectorId: String) {
        prefs.setActiveConnectorId(connectorId)
    }
}

class SearchHistoryRepository(private val database: AppDatabase) {
    fun observeRecent(): Flow<List<SearchHistoryEntity>> {
        return database.searchHistoryDao().observeRecent()
    }

    suspend fun record(query: String) = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext
        database.searchHistoryDao().deleteByQuery(trimmed)
        database.searchHistoryDao().insert(
            SearchHistoryEntity(query = trimmed, searchedAt = System.currentTimeMillis())
        )
    }

    suspend fun remove(id: Long) = withContext(Dispatchers.IO) {
        database.searchHistoryDao().deleteById(id)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        database.searchHistoryDao().clearAll()
    }
}

class CollectionRepository(private val database: AppDatabase) {
    fun observeCollections(): Flow<List<CollectionEntity>> {
        return database.collectionDao().observeCollections()
    }

    fun observeItems(collectionId: Long): Flow<List<CollectionItemEntity>> {
        return database.collectionDao().observeItems(collectionId)
    }

    suspend fun create(name: String): Long = withContext(Dispatchers.IO) {
        database.collectionDao().insertCollection(
            CollectionEntity(name = name.trim(), createdAt = System.currentTimeMillis())
        )
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        database.collectionDao().deleteItemsOf(id)
        database.collectionDao().deleteCollection(id)
    }

    suspend fun addItem(collectionId: Long, connectorId: String, uid: String, title: String, cover: String?) = withContext(Dispatchers.IO) {
        database.collectionDao().insertItem(
            CollectionItemEntity(
                collectionId = collectionId,
                connectorId = connectorId,
                uid = uid,
                title = title,
                cover = cover,
                addedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeItem(collectionId: Long, connectorId: String, uid: String) = withContext(Dispatchers.IO) {
        database.collectionDao().deleteItem(collectionId, connectorId, uid)
    }
}
