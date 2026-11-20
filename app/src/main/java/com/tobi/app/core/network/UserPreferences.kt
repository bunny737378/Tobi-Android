package com.tobi.app.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "tobi_prefs")

data class UserPreferencesState(
    val oledBlack: Boolean = false,
    val accentHex: String = "#E04F5F",
    val subtitleFontSize: Float = 16f,
    val subtitleColorHex: String = "#FFFFFF",
    val subtitleBackgroundOpacity: Float = 0.6f,
    val subtitleEdge: String = "outline",
    val subtitleOffsetPx: Int = 0,
    val subtitleDelayMs: Int = 0,
    val audioOffsetMs: Int = 0,
    val playbackSpeed: Float = 1f,
    val aspectMode: String = "fit",
    val decoderMode: String = "auto",
    val autoPlayNext: Boolean = true,
    val proxyEnabled: Boolean = false,
    val proxyHost: String = "",
    val proxyPort: Int = 8080,
    val dohEnabled: Boolean = false,
    val dohUrl: String = "https:" + "/" + "/cloudflare-dns.com/dns-query",
    val maxConcurrentDownloads: Int = 2,
    val activeConnectorId: String? = null,
    val multiConnectorSearch: Boolean = false
)

class UserPreferences(context: Context) {
    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val oledBlackKey = booleanPreferencesKey("oled_black")
    private val accentHexKey = stringPreferencesKey("accent_hex")
    private val subtitleFontSizeKey = floatPreferencesKey("subtitle_font_size")
    private val subtitleColorHexKey = stringPreferencesKey("subtitle_color_hex")
    private val subtitleBackgroundOpacityKey = floatPreferencesKey("subtitle_bg_opacity")
    private val subtitleEdgeKey = stringPreferencesKey("subtitle_edge")
    private val subtitleOffsetPxKey = intPreferencesKey("subtitle_offset_px")
    private val subtitleDelayMsKey = intPreferencesKey("subtitle_delay_ms")
    private val audioOffsetMsKey = intPreferencesKey("audio_offset_ms")
    private val playbackSpeedKey = floatPreferencesKey("playback_speed")
    private val aspectModeKey = stringPreferencesKey("aspect_mode")
    private val decoderModeKey = stringPreferencesKey("decoder_mode")
    private val autoPlayNextKey = booleanPreferencesKey("auto_play_next")
    private val proxyEnabledKey = booleanPreferencesKey("proxy_enabled")
    private val proxyHostKey = stringPreferencesKey("proxy_host")
    private val proxyPortKey = intPreferencesKey("proxy_port")
    private val dohEnabledKey = booleanPreferencesKey("doh_enabled")
    private val dohUrlKey = stringPreferencesKey("doh_url")
    private val maxConcurrentDownloadsKey = intPreferencesKey("max_concurrent_downloads")
    private val activeConnectorIdKey = stringPreferencesKey("active_connector_id")
    private val multiConnectorSearchKey = booleanPreferencesKey("multi_connector_search")

    val state: StateFlow<UserPreferencesState> = appContext.userDataStore.data
        .map { prefs ->
            UserPreferencesState(
                oledBlack = prefs[oledBlackKey] ?: false,
                accentHex = prefs[accentHexKey] ?: "#E04F5F",
                subtitleFontSize = prefs[subtitleFontSizeKey] ?: 16f,
                subtitleColorHex = prefs[subtitleColorHexKey] ?: "#FFFFFF",
                subtitleBackgroundOpacity = prefs[subtitleBackgroundOpacityKey] ?: 0.6f,
                subtitleEdge = prefs[subtitleEdgeKey] ?: "outline",
                subtitleOffsetPx = prefs[subtitleOffsetPxKey] ?: 0,
                subtitleDelayMs = prefs[subtitleDelayMsKey] ?: 0,
                audioOffsetMs = prefs[audioOffsetMsKey] ?: 0,
                playbackSpeed = prefs[playbackSpeedKey] ?: 1f,
                aspectMode = prefs[aspectModeKey] ?: "fit",
                decoderMode = prefs[decoderModeKey] ?: "auto",
                autoPlayNext = prefs[autoPlayNextKey] ?: true,
                proxyEnabled = prefs[proxyEnabledKey] ?: false,
                proxyHost = prefs[proxyHostKey] ?: "",
                proxyPort = prefs[proxyPortKey] ?: 8080,
                dohEnabled = prefs[dohEnabledKey] ?: false,
                dohUrl = prefs[dohUrlKey] ?: "https:" + "/" + "/cloudflare-dns.com/dns-query",
                maxConcurrentDownloads = prefs[maxConcurrentDownloadsKey] ?: 2,
                activeConnectorId = prefs[activeConnectorIdKey],
                multiConnectorSearch = prefs[multiConnectorSearchKey] ?: false
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, UserPreferencesState())

    private fun update(block: suspend (MutablePreferences) -> Unit) {
        scope.launch {
            appContext.userDataStore.edit { prefs -> block(prefs) }
        }
    }

    fun setOledBlack(value: Boolean) = update { it[oledBlackKey] = value }
    fun setAccentHex(value: String) = update { it[accentHexKey] = value }
    fun setSubtitleFontSize(value: Float) = update { it[subtitleFontSizeKey] = value }
    fun setSubtitleColorHex(value: String) = update { it[subtitleColorHexKey] = value }
    fun setSubtitleBackgroundOpacity(value: Float) = update { it[subtitleBackgroundOpacityKey] = value }
    fun setSubtitleEdge(value: String) = update { it[subtitleEdgeKey] = value }
    fun setSubtitleOffsetPx(value: Int) = update { it[subtitleOffsetPxKey] = value }
    fun setSubtitleDelayMs(value: Int) = update { it[subtitleDelayMsKey] = value }
    fun setAudioOffsetMs(value: Int) = update { it[audioOffsetMsKey] = value }
    fun setPlaybackSpeed(value: Float) = update { it[playbackSpeedKey] = value }
    fun setAspectMode(value: String) = update { it[aspectModeKey] = value }
    fun setDecoderMode(value: String) = update { it[decoderModeKey] = value }
    fun setAutoPlayNext(value: Boolean) = update { it[autoPlayNextKey] = value }
    fun setProxyEnabled(value: Boolean) = update { it[proxyEnabledKey] = value }
    fun setProxyHost(value: String) = update { it[proxyHostKey] = value }
    fun setProxyPort(value: Int) = update { it[proxyPortKey] = value }
    fun setDohEnabled(value: Boolean) = update { it[dohEnabledKey] = value }
    fun setDohUrl(value: String) = update { it[dohUrlKey] = value }
    fun setMaxConcurrentDownloads(value: Int) = update { it[maxConcurrentDownloadsKey] = value }
    fun setActiveConnectorId(value: String?) = update {
        if (value == null) it.remove(activeConnectorIdKey) else it[activeConnectorIdKey] = value
    }
    fun setMultiConnectorSearch(value: Boolean) = update { it[multiConnectorSearchKey] = value }
}
