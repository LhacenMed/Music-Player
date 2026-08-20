package org.fossify.musicplayer.playback

import android.content.Context
import androidx.media3.common.MediaItem
import com.bumptech.glide.Glide
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.data.extractAccentColor
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.loadTrackCoverArt
import org.fossify.musicplayer.extensions.toTrack
import org.fossify.musicplayer.models.Events
import org.fossify.musicplayer.models.Track
import org.greenrobot.eventbus.EventBus

/**
 * Keeps [PlaybackService.currentAccentColor] pointed at the playing track's own cover color, when
 * [org.fossify.musicplayer.helpers.Config.adaptiveTrackTheme] is turned on.
 *
 * Extraction reads the cover's pixels, so it runs off the main thread, and its result is cached
 * per track: revisiting a track already extracted this session is instant. A track change starts
 * a new request before an older one may have resolved; [applyColor] drops any result that arrives
 * after a newer one has already landed, the same way [PlayHistoryRecorder] drops a stale count.
 *
 * This lives with the service rather than with any screen, so the accent is already settled by the
 * time a screen opens, and stays correct from the notification or Android Auto alike.
 */
class AdaptiveAccentController(private val context: Context) {
    private val cache = object : LinkedHashMap<Long, Int>(CACHE_CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Int>) = size > CACHE_CAPACITY
    }

    /** The track a resolving extraction was for; a result for anything else is stale. */
    private var pendingTrackId: Long? = null

    fun onTrackStarted(mediaItem: MediaItem?) {
        val track = mediaItem?.toTrack()
        pendingTrackId = track?.mediaStoreId

        if (!context.config.adaptiveTrackTheme || track == null) {
            applyColor(null, forTrackId = track?.mediaStoreId)
            return
        }

        val cachedColor = cache[track.mediaStoreId]
        if (cachedColor != null) {
            applyColor(cachedColor, forTrackId = track.mediaStoreId)
            return
        }

        ensureBackgroundThread {
            val color = context.resolveCoverBitmap(track)?.extractAccentColor()
            if (color != null) {
                cache[track.mediaStoreId] = color
            }

            applyColor(color, forTrackId = track.mediaStoreId)
        }
    }

    /** Re-run extraction for whatever is currently playing, e.g. right after the setting is toggled on. */
    fun refresh() = onTrackStarted(PlaybackService.currentMediaItem)

    private fun applyColor(color: Int?, forTrackId: Long?) {
        if (forTrackId != pendingTrackId || PlaybackService.currentAccentColor == color) {
            return
        }

        PlaybackService.setAccentColor(color)
        EventBus.getDefault().post(Events.AccentColorChanged())
    }

    private fun Context.resolveCoverBitmap(track: Track) = track.coverArt.ifEmpty { null }
        ?.let { coverArt -> runCatching { Glide.with(this).asBitmap().load(coverArt).submit().get() }.getOrNull() }
        ?: loadTrackCoverArt(track)

    private companion object {
        const val CACHE_CAPACITY = 30
    }
}
