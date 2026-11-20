package com.tobi.app.core.player

import android.app.PictureInPictureParams
import android.os.Bundle
import android.util.Rational
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.tobi.app.TobiApplication
import com.tobi.app.core.runtime.OpResult
import kotlinx.coroutines.launch

@UnstableApi
class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private var request: PlayerRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        request = PlayerSessionStore.current
        playerView = PlayerView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            useController = true
            keepScreenOn = true
        }
        setContentView(playerView)
        val activeRequest = request
        if (activeRequest == null) {
            showError("Nothing to play")
            return
        }
        lifecycleScope.launch { prepare(activeRequest) }
    }

    private suspend fun prepare(activeRequest: PlayerRequest) {
        val container = (application as TobiApplication).container
        val prefs = container.userPreferences.state.value
        val exoPlayer = PlayerEngine.buildPlayer(this, prefs.decoderMode)
        player = exoPlayer
        playerView.player = exoPlayer
        PlayerEngine.applySpeed(exoPlayer, prefs.playbackSpeed)
        PlayerEngine.applyAspect(playerView, prefs.aspectMode)
        val mediaSource = if (!activeRequest.localFilePath.isNullOrBlank()) {
            PlayerEngine.buildLocalMediaSource(this, activeRequest.localFilePath)
        } else {
            val runtime = container.runtimeForId(activeRequest.connectorId)
            val targetId = activeRequest.targetId ?: activeRequest.unitId
            if (runtime == null || targetId.isNullOrBlank()) {
                showError("Playback target is unavailable")
                return
            }
            when (val result = runtime.resolveMedia(targetId, activeRequest.carry, activeRequest.net)) {
                is OpResult.Err -> {
                    showError("Could not resolve media")
                    return
                }
                is OpResult.Ok -> {
                    val source = result.envelope.payload?.sources?.sortedByDescending { it.priority }?.firstOrNull { it.uri != null }
                    if (source == null) {
                        showError(result.envelope.fault?.summary ?: "No playable source")
                        return
                    }
                    val connector = container.connectorManager.getInstalled(activeRequest.connectorId)
                    PlayerEngine.buildMediaSource(this, source, connector?.manifest()?.defaultNet, container.vault)
                }
            }
        }
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        if (activeRequest.startPositionMs > 0) exoPlayer.seekTo(activeRequest.startPositionMs)
        exoPlayer.playWhenReady = true
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                showError(error.message ?: "Playback failed")
            }
        })
    }

    private fun showError(message: String) {
        playerView.addView(TextView(this).apply {
            text = message
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0xAA000000.toInt())
            setPadding(32, 24, 32, 24)
        })
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val current = player ?: return
        if (current.isPlaying && android.os.Build.VERSION.SDK_INT >= 26) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }
    }

    override fun onStop() {
        super.onStop()
        val activeRequest = request ?: return
        val current = player ?: return
        if (!isInPictureInPictureMode && current.duration > 0) {
            val container = (application as TobiApplication).container
            lifecycleScope.launch {
                container.historyRepository.recordProgress(
                    connectorId = activeRequest.connectorId,
                    uid = activeRequest.uid,
                    title = activeRequest.title,
                    kind = activeRequest.kind,
                    cover = activeRequest.cover,
                    banner = activeRequest.banner,
                    positionMs = current.currentPosition,
                    durationMs = current.duration,
                    groupId = activeRequest.groupId,
                    unitId = activeRequest.unitId,
                    episodeLabel = activeRequest.episodeLabel,
                    targetId = activeRequest.targetId,
                    carry = activeRequest.carry,
                    isLive = activeRequest.isLive
                )
            }
        }
    }

    override fun onDestroy() {
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
