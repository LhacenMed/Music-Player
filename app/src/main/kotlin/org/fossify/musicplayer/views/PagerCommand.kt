package org.fossify.musicplayer.views

import org.fossify.musicplayer.models.Track

/**
 * A single, self-contained instruction for the playback panel's cover carousel.
 *
 * The queue travels with the instruction rather than being read back when the instruction is
 * applied, so the data the carousel lands on and the page it is told to scroll to can never come
 * from two different moments. That pairing is the whole point: a carousel told to scroll to an
 * entry its adapter has not been given yet silently clamps to the end of the queue it still holds,
 * and stays on the previous track's cover.
 *
 * @param tracks The queue the carousel should be showing, in the order skip next/previous
 *   traverses it.
 * @param scrollTo The page the carousel should settle on.
 * @param replacesQueue Whether [tracks] is a different queue that has to be re-seated, or the one
 *   already on screen with only the playing entry having moved along it. Only the latter reads
 *   well as an animated move.
 */
data class PagerCommand(
    val tracks: List<Track>,
    val scrollTo: Int,
    val replacesQueue: Boolean
)
