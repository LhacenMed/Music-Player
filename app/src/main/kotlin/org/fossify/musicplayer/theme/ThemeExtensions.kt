package org.fossify.musicplayer.theme

import androidx.compose.ui.graphics.Color

/** Dims a colour for a disabled row, so one component decides what "disabled" looks like. */
fun Color.applyOpacity(enabled: Boolean): Color =
    if (enabled) this else copy(alpha = 0.62f)
