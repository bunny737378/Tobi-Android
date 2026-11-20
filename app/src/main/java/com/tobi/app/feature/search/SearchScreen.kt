package com.tobi.app.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobi.app.core.designsystem.EmptyState
import com.tobi.app.core.designsystem.EntryCardView
import com.tobi.app.core.designsystem.ErrorBanner
import com.tobi.app.core.designsystem.LoadingIndicator
import com.tobi.app.core.designsystem.TobiSearchBar
import com.tobi.app.core.protocol.CatalogRequest
import com.tobi.app.core.protocol.EntryCard
import com.tobi.app.core.runtime.OpResult
import com.tobi.app.core.runtime.userMessage
import com.tobi.app.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val connectorId: String? = null,
    val entries: List<EntryCard> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class SearchViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()
    private var searchJob: Job? = null

    fun setQuery(value: String) {
        mutableState.value = mutableState.value.copy(query = value)
        searchJob?.cancel()
        if (value.isBlank()) {
            mutableState.value = mutableState.value.copy(entries = emptyList(), loading = false, error = null)
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            search(value)
        }
    }

    fun searchNow() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { search(state.value.query) }
    }

    private suspend fun search(query: String) {
        val connector = container.connectorRepository.activeConnector.first()
        if (connector == null) {
            mutableState.value = mutableState.value.copy(error = "Install a connector first")
            return
        }
        mutableState.value = mutableState.value.copy(loading = true, error = null, connectorId = connector.connectorId)
        container.searchHistoryRepository.record(query)
        when (val result = container.runtimeFor(connector).queryCatalog(CatalogRequest(keyword = query))) {
            is OpResult.Ok -> mutableState.value = mutableState.value.copy(
                loading = false,
                entries = result.envelope.payload?.entries.orEmpty(),
                error = result.envelope.fault?.summary
            )
            is OpResult.Err -> mutableState.value = mutableState.value.copy(loading = false, error = result.error.userMessage())
        }
    }
}

@Composable
fun SearchScreen(viewModel: SearchViewModel, onOpenEntry: (String, EntryCard) -> Unit) {
    val state by viewModel.state.collectAsState()
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        TobiSearchBar(state.query, viewModel::setQuery, Modifier.padding(16.dp))
        state.error?.let { ErrorBanner(it, viewModel::searchNow) }
        when {
            state.loading -> LoadingIndicator(Modifier.fillMaxSize())
            state.query.isBlank() -> EmptyState(title = "Search", message = "Find films, series, episodes, and live channels.")
            state.entries.isEmpty() -> EmptyState(title = "No results", message = "Try another title or keyword.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(132.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 110.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(state.entries, key = { it.uid }) { entry ->
                    EntryCardView(entry, onClick = { state.connectorId?.let { onOpenEntry(it, entry) } })
                }
            }
        }
    }
}
