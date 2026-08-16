package org.fossify.musicplayer.models

/**
 * The lyrics embedded in a track's audio file.
 *
 * Both variants expose their content as [lines] so that consumers can render synced and plain
 * lyrics with the same machinery, and only opt into timing when it is actually available.
 */
sealed interface Lyrics {
    /** Every lyric line, in display order. */
    val lines: List<String>

    /**
     * Time-synchronized lyrics that can be followed alongside playback.
     *
     * @param timedLines Every lyric line paired with the position it becomes active at, sorted
     *   ascending by that position.
     */
    data class Synced(val timedLines: List<TimedLine>) : Lyrics {
        override val lines = timedLines.map(TimedLine::text)
    }

    /** Plain lyrics carrying no timing information. */
    data class Plain(override val lines: List<String>) : Lyrics
}

/**
 * A single time-anchored lyric line.
 *
 * @param startMs The playback position at which this line becomes active, in milliseconds.
 * @param text The line's text. Empty for the instrumental breaks that LRC files use as spacers.
 */
data class TimedLine(val startMs: Long, val text: String)
