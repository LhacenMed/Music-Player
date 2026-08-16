package org.fossify.musicplayer.views

import org.fossify.musicplayer.models.Lyrics

/** The lyrics display state of the currently playing track. */
sealed interface LyricsState {
    /**
     * Lyrics are still being read from the track's file. Renders blank rather than as [Empty], so
     * that a track with lyrics never flashes a "no lyrics" placeholder before they arrive.
     */
    data object Loading : LyricsState

    /** Nothing is playing, or the playing track has no usable lyrics. */
    data object Empty : LyricsState

    /** Lyrics were found for the playing track. */
    data class Loaded(val lyrics: Lyrics) : LyricsState
}
