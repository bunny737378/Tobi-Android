package com.tobi.app.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobi.app.core.database.WatchHistoryEntity
import com.tobi.app.core.database.WatchlistEntity
import com.tobi.app.core.designsystem.ArtworkImage
import com.tobi.app.core.designsystem.EmptyState
import com.tobi.app.core.designsystem.FilterChipView
import com.tobi.app.core.designsystem.LocalTobiPalette
import com.tobi.app.core.player.PlayerRequest
import com.tobi.app.core.protocol.ConnectorJson
import com.tobi.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

data class LibraryUiState(
    val history: List<WatchHistoryEntity> = emptyList(),
    val watchlist: List<WatchlistEntity> = emptyList()
)

class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<LibraryUiState> = combine(
        container.historyRepository.observeContinueWatching(),
        container.watchlistRepository.observeWatchlist()
    ) { history, watchlist -> LibraryUiState(history, watchlist) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

    fun resume(item: WatchHistoryEntity): PlayerRequest {
        val carry = runCatching {
            ConnectorJson.decodeFromString(
                MapSerializer(String.serializer(), String.serializer()), item.carryJson
            )
        }.getOrDefault(emptyMap())
        return PlayerRequest(
            connectorId = item.connectorId,
            uid = item.uid,
            title = item.title,
            targetId = item.targetId,
            unitId = item.unitId,
            carry = carry,
            cover = item.cover,
            banner = item.banner,
            kind = item.kind,
            groupId = item.groupId,
            episodeLabel = item.episodeLabel,
            startPositionMs = item.positionMs,
            isLive = item.isLive
        )
    }
}

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenEntry: (String, String) -> Unit,
    onResume: (PlayerRequest) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val selected = androidx.compose.runtime.remember { MutableStateFlow(0) }
    val tab by selected.collectAsState()
    val palette = LocalTobiPalette.current
    Column(Modifier.fillMaxSize()) {
        Text("Library", color = palette.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(20.dp))
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChipView("History", tab == 0) { selected.value = 0 }
            FilterChipView("Watchlist", tab == 1) { selected.value = 1 }
        }
        if (tab == 0) {
            if (state.history.isEmpty()) EmptyState(title = "No history", message = "What you watch will appear here.")
            else LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 12.dp, 12.dp, 110.dp)) {
                items(state.history, key = { it.id }) { item ->
                    LibraryRow(item.title, item.episodeLabel, item.cover) { onResume(viewModel.resume(item)) }
                }
            }
        } else {
            if (state.watchlist.isEmpty()) EmptyState(title = "Watchlist is empty", message = "Save a title from its details page.")
            else LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 12.dp, 12.dp, 110.dp)) {
                items(state.watchlist, key = { it.id }) { item ->
                    LibraryRow(item.title, item.kind, item.cover) { onOpenEntry(item.connectorId, item.uid) }
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(title: String, subtitle: String?, image: String?, onClick: () -> Unit) {
    val palette = LocalTobiPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkImage(image, modifier = Modifier.size(72.dp), fallbackTitle = title)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = palette.textPrimary)
            subtitle?.let { Text(it, color = palette.textMuted) }
        }
    }
}
