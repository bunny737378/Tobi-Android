package com.tobi.app.feature.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobi.app.core.designsystem.ArtworkImage
import com.tobi.app.core.designsystem.EmptyState
import com.tobi.app.core.designsystem.ErrorBanner
import com.tobi.app.core.designsystem.GenreChips
import com.tobi.app.core.designsystem.LoadingIndicator
import com.tobi.app.core.designsystem.LocalTobiPalette
import com.tobi.app.core.designsystem.MetadataRow
import com.tobi.app.core.designsystem.TobiButton
import com.tobi.app.core.designsystem.TobiOutlinedButton
import com.tobi.app.core.player.PlayerRequest
import com.tobi.app.core.protocol.MediaGroup
import com.tobi.app.core.protocol.ProfilePayload
import com.tobi.app.core.protocol.UnitItem
import com.tobi.app.core.protocol.isDownloadOnly
import com.tobi.app.core.runtime.EntryContextStore
import com.tobi.app.core.runtime.OpResult
import com.tobi.app.core.runtime.userMessage
import com.tobi.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailsUiState(
    val connectorId: String = "",
    val uid: String = "",
    val entryCarry: Map<String, String> = emptyMap(),
    val profile: ProfilePayload? = null,
    val loading: Boolean = false,
    val loadingGroups: Set<String> = emptySet(),
    val error: String? = null
)

class DetailsViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(DetailsUiState())
    val state: StateFlow<DetailsUiState> = mutableState.asStateFlow()

    fun load(connectorId: String, uid: String) {
        if (state.value.connectorId == connectorId && state.value.uid == uid && state.value.profile != null) return
        val entry = EntryContextStore.get(connectorId, uid)
        mutableState.value = DetailsUiState(
            connectorId = connectorId,
            uid = uid,
            entryCarry = entry?.carry.orEmpty(),
            loading = true
        )
        viewModelScope.launch {
            val runtime = container.runtimeForId(connectorId)
            if (runtime == null) {
                mutableState.value = mutableState.value.copy(loading = false, error = "Connector not found")
                return@launch
            }
            when (val result = runtime.fetchProfile(uid, entry?.carry.orEmpty(), entry?.visuals?.net)) {
                is OpResult.Ok -> mutableState.value = mutableState.value.copy(
                    profile = result.envelope.payload,
                    loading = false,
                    error = null
                )
                is OpResult.Err -> mutableState.value = mutableState.value.copy(
                    loading = false,
                    error = result.error.userMessage()
                )
            }
        }
    }

    fun loadGroup(group: MediaGroup) {
        if (!group.deferredUnits || group.units.isNotEmpty() || group.groupId in state.value.loadingGroups) return
        val connectorId = state.value.connectorId
        mutableState.value = state.value.copy(loadingGroups = state.value.loadingGroups + group.groupId, error = null)
        viewModelScope.launch {
            val runtime = container.runtimeForId(connectorId)
            if (runtime == null) {
                mutableState.value = mutableState.value.copy(
                    loadingGroups = mutableState.value.loadingGroups - group.groupId,
                    error = "Connector not found"
                )
                return@launch
            }
            when (val result = runtime.fetchUnits(group.groupId, 1, null, group.carry)) {
                is OpResult.Ok -> {
                    val units = result.envelope.payload?.units.orEmpty()
                    val profile = mutableState.value.profile
                    mutableState.value = mutableState.value.copy(
                        profile = profile?.copy(
                            groups = profile.groups.map { current ->
                                if (current.groupId == group.groupId) current.copy(units = units, deferredUnits = false) else current
                            }
                        ),
                        loadingGroups = mutableState.value.loadingGroups - group.groupId
                    )
                }
                is OpResult.Err -> mutableState.value = mutableState.value.copy(
                    loadingGroups = mutableState.value.loadingGroups - group.groupId,
                    error = result.error.userMessage()
                )
            }
        }
    }

    fun toggleWatchlist() {
        val profile = state.value.profile ?: return
        viewModelScope.launch {
            container.watchlistRepository.toggle(state.value.connectorId, profile.uid, profile.name, profile.visuals?.cover, profile.kind)
        }
    }

    fun playerRequest(unit: UnitItem? = null): PlayerRequest? {
        val profile = state.value.profile ?: return null
        val target = unit?.unitId ?: profile.directTarget?.targetId ?: return null
        return PlayerRequest(
            connectorId = state.value.connectorId,
            uid = profile.uid,
            title = profile.name,
            subtitle = unit?.name,
            targetId = target,
            unitId = unit?.unitId,
            carry = unit?.carry ?: profile.directTarget?.carry.orEmpty(),
            net = unit?.net ?: profile.directTarget?.net,
            cover = profile.visuals?.cover,
            banner = profile.visuals?.banner,
            kind = profile.kind,
            groupId = profile.groups.firstOrNull { group -> group.units.any { it.unitId == unit?.unitId } }?.groupId,
            episodeLabel = unit?.name,
            isLive = profile.liveSchedule != null,
            downloadOnly = profile.isDownloadOnly()
        )
    }
}

@Composable
fun DetailsScreen(
    connectorId: String,
    uid: String,
    viewModel: DetailsViewModel,
    onPlay: (PlayerRequest) -> Unit
) {
    androidx.compose.runtime.LaunchedEffect(connectorId, uid) { viewModel.load(connectorId, uid) }
    val state by viewModel.state.collectAsState()
    val profile = state.profile
    val palette = LocalTobiPalette.current
    when {
        state.loading -> LoadingIndicator(Modifier.fillMaxSize())
        profile == null -> EmptyState(title = "Unavailable", message = state.error ?: "This item could not be loaded.")
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 80.dp)) {
            item {
                ArtworkImage(
                    url = profile.visuals?.banner ?: profile.visuals?.cover,
                    headers = profile.visuals?.net?.headers.orEmpty(),
                    modifier = Modifier.fillMaxWidth().height(250.dp),
                    fallbackTitle = profile.name,
                    cornerRadius = 0.dp
                )
                Column(Modifier.padding(20.dp)) {
                    Text(profile.name, color = palette.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(8.dp))
                    MetadataRow(listOfNotNull(profile.kind, profile.facts?.releaseYear?.toString(), profile.facts?.runtimeMin?.let { "$it min" }))
                    GenreChips(profile.genres)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        viewModel.playerRequest()?.let { request ->
                            val downloadOnly = profile.isDownloadOnly()
                            TobiButton(
                                if (downloadOnly) "Download" else "Play",
                                { onPlay(request) },
                                icon = if (downloadOnly) Icons.Filled.Download else Icons.Filled.PlayArrow
                            )
                        }
                        TobiOutlinedButton("Watchlist", viewModel::toggleWatchlist, icon = Icons.Filled.BookmarkAdd)
                    }
                    profile.synopsis?.let {
                        Spacer(Modifier.height(20.dp))
                        Text(it, color = palette.textSecondary)
                    }
                }
            }
            profile.groups.forEach { group ->
                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(group.label ?: "Episodes", color = palette.textPrimary)
                        if (group.deferredUnits && group.units.isEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            TobiOutlinedButton(
                                text = if (group.groupId in state.loadingGroups) "Loading…" else "Load episodes",
                                onClick = { viewModel.loadGroup(group) }
                            )
                        }
                    }
                }
                items(group.units.size, key = { group.units[it].unitId }) { index ->
                    val unit = group.units[index]
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.playerRequest(unit)?.let(onPlay) }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArtworkImage(
                            unit.visuals?.thumb,
                            headers = unit.visuals?.net?.headers.orEmpty(),
                            modifier = Modifier.height(72.dp).fillMaxWidth(0.32f),
                            fallbackTitle = unit.index?.toString()
                        )
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(unit.name ?: "Episode ${unit.index ?: index + 1}", color = palette.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            unit.synopsis?.let { Text(it, color = palette.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = palette.textPrimary)
                    }
                }
            }
            state.error?.let { item { ErrorBanner(it) } }
        }
    }
}
