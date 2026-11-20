package com.tobi.app.feature.connectors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobi.app.core.designsystem.EmptyState
import com.tobi.app.core.designsystem.ErrorBanner
import com.tobi.app.core.designsystem.LocalTobiPalette
import com.tobi.app.core.designsystem.TobiButton
import com.tobi.app.core.designsystem.TobiOutlinedButton
import com.tobi.app.core.runtime.InstalledConnector
import com.tobi.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConnectorManagerViewModel(private val container: AppContainer) : ViewModel() {
    val connectors: StateFlow<List<InstalledConnector>> = container.connectorRepository.observeInstalled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()

    fun install(url: String) = viewModelScope.launch {
        mutableMessage.value = null
        runCatching {
            if (url.substringBefore('?').endsWith(".json", true)) container.connectorManager.installFromManifestUrl(url)
            else container.connectorManager.installFromJsUrl(url)
        }.onSuccess { container.connectorRepository.setActive(it.connectorId) }
            .onFailure { mutableMessage.value = it.message ?: "Install failed" }
    }

    fun setActive(id: String) = container.connectorRepository.setActive(id)
    fun setEnabled(id: String, enabled: Boolean) = viewModelScope.launch {
        container.connectorManager.setEnabled(id, enabled)
        container.invalidateRuntime(id)
    }
    fun update(id: String) = viewModelScope.launch {
        runCatching { container.connectorManager.update(id) }
            .onSuccess { container.invalidateRuntime(id) }
            .onFailure { mutableMessage.value = it.message }
    }
    fun uninstall(id: String) = viewModelScope.launch {
        container.invalidateRuntime(id)
        container.connectorManager.uninstall(id)
    }
}

@Composable
fun ConnectorManagerScreen(viewModel: ConnectorManagerViewModel) {
    val connectors by viewModel.connectors.collectAsState()
    val message by viewModel.message.collectAsState()
    var url by remember { mutableStateOf("") }
    val palette = LocalTobiPalette.current
    Column(Modifier.fillMaxSize()) {
        Text("Connectors", color = palette.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(20.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Connector JS or manifest URL") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            singleLine = true
        )
        TobiButton("Install", { viewModel.install(url.trim()); url = "" }, Modifier.padding(20.dp), enabled = url.startsWith("http"))
        message?.let { ErrorBanner(it) }
        if (connectors.isEmpty()) {
            EmptyState(title = "No connectors installed", message = "Paste a connector URL above to get started.")
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 0.dp, 12.dp, 100.dp)) {
                items(connectors, key = { it.connectorId }) { connector ->
                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(connector.displayName, color = palette.textPrimary)
                                Text("${connector.connectorId} · ${connector.version}", color = palette.textMuted)
                            }
                            Switch(checked = connector.enabled, onCheckedChange = { viewModel.setEnabled(connector.connectorId, it) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TobiOutlinedButton("Use", { viewModel.setActive(connector.connectorId) })
                            if (connector.sourceUrl != null) TobiOutlinedButton("Update", { viewModel.update(connector.connectorId) })
                            TobiOutlinedButton("Remove", { viewModel.uninstall(connector.connectorId) })
                        }
                    }
                }
            }
        }
    }
}
