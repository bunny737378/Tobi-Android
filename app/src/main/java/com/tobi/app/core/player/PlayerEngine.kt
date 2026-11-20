package com.tobi.app.core.player

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AudioTrackPlaybackParams
import androidx.media3.common.C
import androidx.media3.common.DrmConfiguration
import androidx.media3.common.MediaCodecInfo
import androidx.media3.common.MediaCodecSelector
import androidx.media3.common.MediaCodecUtil
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.text.Cue
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import com.tobi.app.core.network.SessionVault
import com.tobi.app.core.network.NetworkModule
import com.tobi.app.core.protocol.CaptionSyntax
import com.tobi.app.core.protocol.ContainerKinds
import com.tobi.app.core.protocol.Encryption
import com.tobi.app.core.protocol.NetPolicy
import com.tobi.app.core.protocol.StreamSource
import com.tobi.app.core.protocol.normalizeCaptionSyntax
import com.tobi.app.core.protocol.normalizeContainer
import java.io.File

class DecoderPolicySelector(private val mode: String) : MediaCodecSelector {
    override fun selectDecoder(mimeType: String, requiresSecureDecoder: Boolean, requiresTunnelingDecoder: Boolean): MediaCodecInfo? {
        val infos = MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        if (infos.isEmpty()) return null
        return when (mode) {
            "sw" -> infos.firstOrNull { it.isSoftwareDecoder } ?: infos.first()
            "hw" -> infos.firstOrNull { !it.isSoftwareDecoder } ?: infos.first()
            else -> infos.first()
        }
    }
}

object PlayerEngine {
    fun buildPlayer(context: Context, decoderMode: String): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setEnableAudioTrackPlaybackParams(true)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30000, 60000, 1000, 2000)
            .build()
        val player = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .setMediaCodecSelector(DecoderPolicySelector(decoderMode))
            .build()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build(),
            true
        )
        player.setWakeMode(C.WAKE_MODE_NETWORK)
        player.setHandleAudioBecomingNoisy(true)
        player.setSeekBackIncrementMs(10000)
        player.setSeekForwardIncrementMs(10000)
        return player
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
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setHttpDataSourceFactory(httpFactory)
        val mime = mimeForContainer(source.container, uri)
        val captions = source.captions.map { caption ->
            MediaItem.SubtitleConfiguration.Builder(caption.uri)
                .setMimeType(mimeForCaption(caption.syntax))
                .setSelectionFlags(if (caption.defaultTrack) C.SELECTION_FLAG_DEFAULT else 0)
                .setLanguage(caption.langCode ?: "")
                .setLabel(caption.label ?: caption.langCode ?: "Subtitle")
                .build()
        }
        val builder = MediaItem.Builder()
            .setUri(Uri.parse(uri))
            .setMediaId(source.sourceId)
            .setSubtitleConfigurations(captions)
        if (mime != null) builder.setMimeType(mime)
        source.encryption?.let { encryption ->
            builder.setDrmConfiguration(buildDrm(encryption))
        }
        val mainSource = mediaSourceFactory.createMediaSource(builder.build())
        if (source.sidecarAudio.isEmpty()) return mainSource
        val merging = MergingMediaSource.Builder().addMediaSource(mainSource)
        source.sidecarAudio.forEach { audio ->
            val audioHeaders = vault.buildHeaders(audio.net, connectorDefault, audio.uri)
            val audioHttp = DefaultHttpDataSource.Factory()
                .setUserAgent(audioHeaders["User-Agent"] ?: SessionVault.DEFAULT_USER_AGENT)
                .setDefaultRequestProperties(audioHeaders)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(20000)
            val audioFactory = DefaultMediaSourceFactory(context)
                .setHttpDataSourceFactory(audioHttp)
            val audioItem = MediaItem.Builder()
                .setUri(Uri.parse(audio.uri))
                .setMediaId("audio_${audio.uri.hashCode()}")
                .build()
            merging.addMediaSource(audioFactory.createMediaSource(audioItem))
        }
        return merging.build()
    }

    fun buildLocalMediaSource(context: Context, filePath: String): MediaSource {
        val file = File(filePath)
        val item = MediaItem.Builder()
            .setUri(file.toUri())
            .setMediaId("local")
            .build()
        return DefaultMediaSourceFactory(context).createMediaSource(item)
    }

    private fun mimeForContainer(container: String, uri: String): String? {
        return when (normalizeContainer(container)) {
            ContainerKinds.HLS -> MimeTypes.APPLICATION_M3U8
            ContainerKinds.DASH -> MimeTypes.APPLICATION_DASH_MANIFEST
            ContainerKinds.MP4 -> MimeTypes.VIDEO_MP4
            ContainerKinds.MKV -> MimeTypes.VIDEO_MATROSKA
            ContainerKinds.WEBM -> MimeTypes.VIDEO_WEBM
            ContainerKinds.TS -> MimeTypes.VIDEO_MP2T
            else -> sniffMime(uri)
        }
    }

    private fun sniffMime(uri: String): String? {
        val lower = uri.lowercase().substringBefore("?").substringBefore("#")
        return when {
            lower.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
            lower.endsWith(".mpd") -> MimeTypes.APPLICATION_DASH_MANIFEST
            lower.endsWith(".mp4") || lower.endsWith(".m4v") -> MimeTypes.VIDEO_MP4
            lower.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA
            lower.endsWith(".webm") -> MimeTypes.VIDEO_WEBM
            lower.endsWith(".ts") -> MimeTypes.VIDEO_MP2T
            else -> null
        }
    }

    private fun mimeForCaption(syntax: String): String {
        return when (normalizeCaptionSyntax(syntax)) {
            CaptionSyntax.SRT -> MimeTypes.TEXT_SRT
            CaptionSyntax.ASS, CaptionSyntax.SSA -> MimeTypes.TEXT_SSA
            CaptionSyntax.TTML -> MimeTypes.TEXT_TTML
            else -> MimeTypes.TEXT_VTT
        }
    }

    private fun buildDrm(encryption: Encryption): DrmConfiguration {
        val scheme = (encryption.scheme ?: "widevine").lowercase()
        return when (scheme) {
            "clearkey", "clear_key", "clear-key" -> {
                val clearKeys = encryption.clearKeys ?: emptyMap()
                val keySetId = clearKeys.entries.joinToString(":") { (keyId, key) ->
                    "${base64Of(keyId)}:${base64Of(key)}"
                }
                DrmConfiguration.Builder(C.DRM_TYPE_CLEARKEY)
                    .setKeySetId(keySetId.toByteArray(Charsets.UTF_8))
                    .build()
            }
            "playready" -> DrmConfiguration.Builder(C.DRM_TYPE_PLAYREADY)
                .setLicenseUri(encryption.licenseUri ?: "")
                .setLicenseRequestHeaders(encryption.licenseHeaders)
                .build()
            else -> DrmConfiguration.Builder(C.DRM_TYPE_WIDEVINE)
                .setLicenseUri(encryption.licenseUri ?: "")
                .setLicenseRequestHeaders(encryption.licenseHeaders)
                .build()
        }
    }

    private fun base64Of(value: String): String {
        return try {
            val decoded = android.util.Base64.decode(value, android.util.Base64.DEFAULT)
            android.util.Base64.encodeToString(decoded, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Base64.encodeToString(value.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        }
    }

    fun applySpeed(player: ExoPlayer, speed: Float) {
        player.setPlaybackParameters(PlaybackParameters(speed.coerceIn(0.25f, 3f)))
    }

    fun applyAspect(frame: AspectRatioFrameLayout, mode: String, videoWidth: Int, videoHeight: Int) {
        when (mode) {
            "fill" -> frame.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL)
            "zoom" -> frame.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
            "stretch" -> {
                frame.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH)
                if (videoWidth > 0 && videoHeight > 0) {
                    frame.setAspectRatio(videoWidth.toFloat() / videoHeight.toFloat())
                }
            }
            "16:9" -> {
                frame.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH)
                frame.setAspectRatio(16f / 9f)
            }
            "4:3" -> {
                frame.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH)
                frame.setAspectRatio(4f / 3f)
            }
            else -> frame.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
        }
    }

    fun applySubtitleStyle(view: DelayedSubtitleView, fontSizeSp: Float, colorHex: String, bgOpacity: Float, edge: String) {
        view.setTextSize(fontSizeSp)
        view.setTextColor(parseColor(colorHex))
        val alpha = (bgOpacity.coerceIn(0f, 1f) * 255).toInt()
        view.setBackgroundColor(android.graphics.Color.argb(alpha, 0, 0, 0))
        when (edge) {
            "none" -> view.setEdgeType(Cue.EDGE_TYPE_NONE)
            "shadow" -> view.setEdgeType(Cue.EDGE_TYPE_SHADOW)
            else -> view.setEdgeType(Cue.EDGE_TYPE_OUTLINE)
        }
        view.setEdgeColor(android.graphics.Color.BLACK)
    }

    fun parseColor(hex: String): Int {
        val cleaned = hex.trim().removePrefix("#")
        if (cleaned.length != 6) return android.graphics.Color.WHITE
        val value = cleaned.toLongOrNull(16) ?: return android.graphics.Color.WHITE
        return (0xFF000000 or value).toInt()
    }

    suspend fun audioOffsetLoop(player: ExoPlayer, offsetMs: () -> Int) {
        var appliedDrift = 0.0
        while (true) {
            kotlinx.coroutines.delay(500)
            val target = offsetMs().toDouble()
            val error = target - appliedDrift
            if (kotlin.math.abs(error) < 5) {
                try {
                    player.setAudioTrackPlaybackParams(
                        AudioTrackPlaybackParams.Builder().setSpeed(1f).setPitch(1f).build()
                    )
                } catch (e: Exception) {
                }
                continue
            }
            val speed = (1.0 + (error / 2000.0).coerceIn(-0.1, 0.1)).toFloat()
            try {
                player.setAudioTrackPlaybackParams(
                    AudioTrackPlaybackParams.Builder().setSpeed(speed).setPitch(1f).build()
                )
            } catch (e: Exception) {
                break
            }
            appliedDrift += (speed - 1.0) * 500.0
        }
    }
}
