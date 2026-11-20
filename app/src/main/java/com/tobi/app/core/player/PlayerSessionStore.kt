package com.tobi.app.core.player

import com.tobi.app.core.protocol.NetPolicy

data class QueueItem(
    val unitId: String,
    val title: String,
    val carry: Map<String, String> = emptyMap(),
    val net: NetPolicy? = null,
    val groupId: String? = null,
    val episodeLabel: String? = null
)

data class PlayerRequest(
    val connectorId: String,
    val uid: String,
    val title: String,
    val subtitle: String? = null,
    val targetId: String? = null,
    val unitId: String? = null,
    val carry: Map<String, String> = emptyMap(),
    val net: NetPolicy? = null,
    val cover: String? = null,
    val banner: String? = null,
    val kind: String? = null,
    val groupId: String? = null,
    val episodeLabel: String? = null,
    val queue: List<QueueItem> = emptyList(),
    val queueIndex: Int = 0,
    val startPositionMs: Long = 0,
    val localFilePath: String? = null,
    val isLive: Boolean = false,
    val downloadOnly: Boolean = false
)

object PlayerSessionStore {
    @Volatile
    var current: PlayerRequest? = null

    fun set(request: PlayerRequest) {
        current = request
    }
}
