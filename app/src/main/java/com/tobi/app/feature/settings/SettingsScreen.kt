package com.tobi.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.tobi.app.core.designsystem.LocalTobiPalette
import com.tobi.app.core.network.UserPreferences
import com.tobi.app.core.network.UserPreferencesState
import com.tobi.app.di.AppContainer
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<UserPreferencesState> = container.userPreferences.state
    private val prefs: UserPreferences = container.userPreferences

    fun setOled(value: Boolean) = prefs.setOledBlack(value)
    fun setAccent(value: String) = prefs.setAccentHex(value)
    fun setSpeed(value: Float) = prefs.setPlaybackSpeed(value)
    fun setSubtitleSize(value: Float) = prefs.setSubtitleFontSize(value)
    fun setDoh(value: Boolean) = prefs.setDohEnabled(value)
    fun setDohUrl(value: String) = prefs.setDohUrl(value)
    fun setProxy(value: Boolean) = prefs.setProxyEnabled(value)
    fun setProxyHost(value: String) = prefs.setProxyHost(value)
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsState()
    val palette = LocalTobiPalette.current
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 20.dp, 20.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { Text("Settings", color = palette.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.headlineLarge) }
        item { ToggleSetting("OLED black", "Use pure black backgrounds", state.oledBlack, viewModel::setOled) }
        item {
            OutlinedTextField(
                value = state.accentHex,
                onValueChange = viewModel::setAccent,
                label = { Text("Accent colour") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            Text("Playback speed ${"%.2f".format(state.playbackSpeed)}×", color = palette.textPrimary)
            Slider(value = state.playbackSpeed, onValueChange = viewModel::setSpeed, valueRange = 0.5f..2f)
        }
        item {
            Text("Subtitle size ${state.subtitleFontSize.toInt()} sp", color = palette.textPrimary)
            Slider(value = state.subtitleFontSize, onValueChange = viewModel::setSubtitleSize, valueRange = 10f..32f)
        }
        item { ToggleSetting("DNS over HTTPS", "Use a custom secure DNS resolver", state.dohEnabled, viewModel::setDoh) }
        if (state.dohEnabled) item {
            OutlinedTextField(state.dohUrl, viewModel::setDohUrl, label = { Text("DoH URL") }, modifier = Modifier.fillMaxWidth())
        }
        item { ToggleSetting("HTTP proxy", "Route connector requests through a proxy", state.proxyEnabled, viewModel::setProxy) }
        if (state.proxyEnabled) item {
            OutlinedTextField(state.proxyHost, viewModel::setProxyHost, label = { Text("Proxy host") }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ToggleSetting(title: String, subtitle: String, value: Boolean, onValueChange: (Boolean) -> Unit) {
    val palette = LocalTobiPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.textPrimary)
            Text(subtitle, color = palette.textMuted)
        }
        Switch(checked = value, onCheckedChange = onValueChange)
    }
}
