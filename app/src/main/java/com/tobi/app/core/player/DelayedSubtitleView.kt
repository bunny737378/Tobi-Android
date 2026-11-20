package com.tobi.app.core.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.text.Cue
import androidx.media3.ui.SubtitleView
import com.google.common.collect.ImmutableList

class DelayedSubtitleView(context: Context) : SubtitleView(context) {
    var delayMs: Long = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCues(cues: ImmutableList<Cue>) {
        if (delayMs <= 0) {
            super.onCues(cues)
        } else {
            handler.postDelayed({ super.onCues(cues) }, delayMs)
        }
    }
}
