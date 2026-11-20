package com.tobi.app.core.player

import android.content.Context
import android.net.Uri
import android.util.TypedValue
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.tobi.app.core.network.SessionVault
import com.tobi.app.core.protocol.Encryption
import com.tobi.app.core.protocol.NetPolicy
import com.tobi.app.core.protocol.StreamSource
import java.io.File

@UnstableApi
object PlayerEngine {
    fun buildPlayer(context: Context, decoderMode: String): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30000, 60000, 1000, 2000)
            .build()
        return ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
                setWakeMode(C.WAKE_MODE_NETWORK)
                setHandleAudioBecomingNoisy(true)
                setSeekBackIncrementMs(10000)
                setSeekForwardIncrementMs(10000)
            }
    }

    fun buildMediaSource(
        context: Context,
        source: StreamSource,
        connectorDefault: NetPolicy?,
        vault: SessionVault
    ): MediaSource {
        val uri = source.uri ?: throw IllegalArgumentException("Source has no URI")
        val headers = vault.buildHeaders(source.net, connectorDefault, uri)
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(headers["User-Agent"] ?: SessionVault.DEFAULT_USER_AGENT)
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000)
        val item = MediaItem.Builder()
            .setUri(Uri.parse(uri))
            .setMediaId(source.sourceId)
            .apply { source.encryption?.let { setDrmConfiguration(buildDrm(it)) } }
            .build()
        return DefaultMediaSourceFactory(
            androidx.media3.datasource.DefaultDataSource.Factory(context, httpFactory)
        ).createMediaSource(item)
    }

    fun buildLocalMediaSource(context: Context, filePath: String): MediaSource {
        val item = MediaItem.Builder().setUri(File(filePath).toUri()).setMediaId("local").build()
        return DefaultMediaSourceFactory(context).createMediaSource(item)
    }

    private fun buildDrm(encryption: Encryption): MediaItem.DrmConfiguration {
        val uuid = when (encryption.scheme?.lowercase()) {
            "clearkey", "clear_key", "clear-key" -> C.CLEARKEY_UUID
            "playready" -> C.PLAYREADY_UUID
            else -> C.WIDEVINE_UUID
        }
        return MediaItem.DrmConfiguration.Builder(uuid).apply {
            encryption.licenseUri?.let { setLicenseUri(it) }
            if (encryption.licenseHeaders.isNotEmpty()) setLicenseRequestHeaders(encryption.licenseHeaders)
        }.build()
    }

    fun applySpeed(player: ExoPlayer, speed: Float) {
        player.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 3f))
    }

    fun applyAspect(view: PlayerView, mode: String) {
        view.resizeMode = when (mode) {
            "fill" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
            "zoom" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            "stretch" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    fun applySubtitleStyle(view: SubtitleView, fontSizeSp: Float, colorHex: String, bgOpacity: Float, edge: String) {
        val edgeType = when (edge) {
            "none" -> CaptionStyleCompat.EDGE_TYPE_NONE
            "shadow" -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
            else -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
        }
        view.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
        view.setStyle(
            CaptionStyleCompat(
                parseColor(colorHex),
                android.graphics.Color.argb((bgOpacity.coerceIn(0f, 1f) * 255).toInt(), 0, 0, 0),
                android.graphics.Color.TRANSPARENT,
                edgeType,
                android.graphics.Color.BLACK,
                null
            )
        )
    }

    fun parseColor(hex: String): Int {
        val cleaned = hex.trim().removePrefix("#")
        val value = cleaned.toLongOrNull(16) ?: return android.graphics.Color.WHITE
        return if (cleaned.length == 6) (0xFF000000 or value).toInt() else value.toInt()
    }

    suspend fun audioOffsetLoop(player: ExoPlayer, offsetMs: () -> Int) {
        while (player.isPlaying) kotlinx.coroutines.delay(500)
    }
}
