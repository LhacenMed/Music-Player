package org.fossify.musicplayer.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.extensions.toTrack
import org.fossify.musicplayer.helpers.PLAY_COUNT_THRESHOLD_MS
import org.fossify.musicplayer.models.Track

/**
 * Keeps the record of what has been listened to, on two separate beats.
 *
 * A track is remembered the moment it starts, which is what puts it at the front of the history
 * playlist however briefly it is heard. Its play count only rises once it has been playing for
 * [PLAY_COUNT_THRESHOLD_MS], so skipping through a queue leaves the counts untouched.
 *
 * This lives with the service rather than with any screen, so listens are recorded just the same
 * from the notification, from Android Auto, or with the app closed.
 */
class PlayHistoryRecorder(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())

    /** The track being listened to, and whether its listen has already been counted. */
    private var playingTrack: Track? = null
    private var isPlayCounted = false

    /**
     * Begin recording a listen to [mediaItem], abandoning whatever was being listened to before.
     *
     * Every transition starts a fresh listen, the same track arriving again included: on repeat-one
     * each time round is a play of its own and is counted as one.
     */
    fun onTrackStarted(mediaItem: MediaItem?) {
        val track = mediaItem?.toTrack()
        cancelPendingCount()
        playingTrack = track
        isPlayCounted = false

        if (track != null) {
            ensureBackgroundThread { context.audioHelper.recordPlayStarted(track) }
        }
    }

    /**
     * Arm or disarm the count for the playing track. The wait is measured from where the track
     * already is, so pausing and resuming does not restart it and seeking past the threshold
     * settles it immediately.
     */
    fun onPlayingChanged(isPlaying: Boolean, currentPositionMs: Long) {
        cancelPendingCount()
        val track = playingTrack
        if (!isPlaying || isPlayCounted || track == null) {
            return
        }

        handler.postDelayed({ countPlay(track) }, (PLAY_COUNT_THRESHOLD_MS - currentPositionMs).coerceAtLeast(0))
    }

    fun release() = cancelPendingCount()

    private fun countPlay(track: Track) {
        isPlayCounted = true
        ensureBackgroundThread { context.audioHelper.recordPlayCounted(track) }
    }

    private fun cancelPendingCount() = handler.removeCallbacksAndMessages(null)
}
