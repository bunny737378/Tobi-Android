package com.tobi.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobi.app.core.designsystem.ContentRail
import com.tobi.app.core.designsystem.EmptyState
import com.tobi.app.core.designsystem.ErrorBanner
import com.tobi.app.core.designsystem.HeroCarousel
import com.tobi.app.core.designsystem.LoadingIndicator
import com.tobi.app.core.designsystem.LocalTobiPalette
import com.tobi.app.core.protocol.EntryCard
import com.tobi.app.core.protocol.FeedPayload
import com.tobi.app.core.runtime.InstalledConnector
import com.tobi.app.core.runtime.OpResult
import com.tobi.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class HomeUiState(
    val connector: InstalledConnector? = null,
    val feed: FeedPayload? = null,
    val loading: Boolean = true,
    val error: String? = null
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            container.connectorRepository.activeConnector.collectLatest { connector ->
                mutableState.value = HomeUiState(connector = connector, loading = connector != null)
                if (connector != null) load(connector)
            }
        }
    }

    fun refresh() {
        state.value.connector?.let { connector -> viewModelScope.launch { load(connector) } }
    }

    private suspend fun load(connector: InstalledConnector) {
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        when (val result = container.runtimeFor(connector).fetchFeed(1, null)) {
            is OpResult.Ok -> mutableState.value = mutableState.value.copy(
                feed = result.envelope.payload,
                loading = false,
                error = result.envelope.fault?.summary
            )
            is OpResult.Err -> mutableState.value = mutableState.value.copy(loading = false, error = "Could not load this connector")
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenEntry: (String, EntryCard) -> Unit,
    onOpenCatalog: (String) -> Unit,
    onOpenConnectors: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val palette = LocalTobiPalette.current
    when {
        state.connector == null -> EmptyState(
            title = "Add a connector",
            message = "Install a connector to browse media.",
            actionLabel = "Connector manager",
            onAction = onOpenConnectors
        )
        state.loading && state.feed == null -> LoadingIndicator(Modifier.fillMaxSize())
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text("TOBI", color = palette.textMuted)
                    Text(state.connector?.displayName.orEmpty(), color = palette.textPrimary)
                }
            }
            state.error?.let { error -> item { ErrorBanner(error, viewModel::refresh) } }
            val feed = state.feed
            if (feed != null) {
                if (feed.spotlight.isNotEmpty()) item {
                    HeroCarousel(
                        entries = feed.spotlight,
                        headersFor = { emptyMap() },
                        onPlay = { onOpenEntry(state.connector!!.connectorId, it) },
                        onDetails = { onOpenEntry(state.connector!!.connectorId, it) }
                    )
                }
                items(feed.shelves.size, key = { feed.shelves[it].shelfId }) { index ->
                    val shelf = feed.shelves[index]
                    ContentRail(
                        shelf = shelf,
                        headersFor = { emptyMap() },
                        onEntryClick = { onOpenEntry(state.connector!!.connectorId, it) },
                        onExpand = { onOpenCatalog(shelf.shelfId) }
                    )
                }
            }
        }
    }
}
