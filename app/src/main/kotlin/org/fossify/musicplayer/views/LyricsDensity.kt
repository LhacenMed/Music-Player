package org.fossify.musicplayer.views

import androidx.annotation.DimenRes
import androidx.annotation.LayoutRes
import org.fossify.musicplayer.R

/**
 * The two sizes a [LyricsView] renders at. Everything that differs between the strip under the
 * cover art and the reading view that replaces it lives here, so both share one timing and
 * scrolling implementation.
 *
 * The ordinals must match the `lyricsDensity` attribute's enum values.
 */
internal enum class LyricsDensity(
    @LayoutRes val lineLayout: Int,
    /** The height of one unwrapped line, used to blank out space above and below the list. */
    @DimenRes val lineHeight: Int,
    @DimenRes val fadeLength: Int
) {
    /** The three-line strip shown under the cover art. */
    COMPACT(R.layout.item_lyric_line, R.dimen.size_lyric_line, R.dimen.size_lyric_fade),

    /** The full-size reading view shown in place of the cover art. */
    EXPANDED(R.layout.item_lyric_line_large, R.dimen.size_lyric_line_large, R.dimen.size_lyric_fade_large)
}
