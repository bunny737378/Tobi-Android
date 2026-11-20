package com.tobi.app.core.player

import android.content.Context
import android.widget.FrameLayout
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView

@UnstableApi
class DelayedSubtitleView(context: Context) : FrameLayout(context) {
    var delayMs: Long = 0
    val subtitleView = SubtitleView(context)

    init {
        addView(subtitleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
}
