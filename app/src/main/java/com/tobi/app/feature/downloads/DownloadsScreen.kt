package com.tobi.app.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobi.app.core.database.DownloadEntity
import com.tobi.app.core.designsystem.EmptyState
import com.tobi.app.core.designsystem.LocalTobiPalette
import com.tobi.app.core.designsystem.ProgressTrack
import com.tobi.app.core.designsystem.formatBytes
import com.tobi.app.core.downloader.DownloadStatus
import com.tobi.app.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(private val container: AppContainer) : ViewModel() {
    val downloads: StateFlow<List<DownloadEntity>> = container.downloadRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun retry(id: Long) = viewModelScope.launch { container.downloadRepository.retry(id) }
    fun delete(id: Long) = viewModelScope.launch { container.downloadRepository.delete(id) }
}

@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel) {
    val downloads by viewModel.downloads.collectAsState()
    val palette = LocalTobiPalette.current
    Column(Modifier.fillMaxSize()) {
        Text("Downloads", color = palette.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(20.dp))
        if (downloads.isEmpty()) {
            EmptyState(title = "No downloads", message = "Offline media will appear here.")
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 0.dp, 12.dp, 110.dp)) {
                items(downloads, key = { it.id }) { item -> DownloadRow(item, viewModel::retry, viewModel::delete) }
            }
        }
    }
}

@Composable
private fun DownloadRow(item: DownloadEntity, retry: (Long) -> Unit, delete: (Long) -> Unit) {
    val palette = LocalTobiPalette.current
    Column(Modifier.fillMaxWidth().padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, color = palette.textPrimary)
                Text(
                    when (item.status) {
                        DownloadStatus.COMPLETED -> formatBytes(item.downloadedBytes)
                        DownloadStatus.FAILED -> item.errorMessage ?: "Failed"
                        else -> "${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}"
                    },
                    color = palette.textMuted
                )
            }
            if (item.status == DownloadStatus.FAILED || item.status == DownloadStatus.PAUSED) {
                IconButton(onClick = { retry(item.id) }) { Icon(Icons.Filled.Refresh, "Retry", tint = palette.textPrimary) }
            }
            IconButton(onClick = { delete(item.id) }) { Icon(Icons.Filled.Delete, "Delete", tint = palette.textMuted) }
        }
        if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED) {
            ProgressTrack(if (item.totalBytes > 0) item.downloadedBytes.toFloat() / item.totalBytes else 0f)
        }
    }
}
