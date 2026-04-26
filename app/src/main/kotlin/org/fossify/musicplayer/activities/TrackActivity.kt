package org.fossify.musicplayer.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.os.postDelayed
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import kotlinx.coroutines.*
import org.fossify.commons.extensions.toast
import org.fossify.musicplayer.R
import org.fossify.musicplayer.extensions.*
import org.fossify.musicplayer.fragments.PlaybackSpeedFragment
import org.fossify.musicplayer.helpers.PlaybackSetting
import org.fossify.musicplayer.interfaces.PlaybackSpeedListener
import org.fossify.musicplayer.models.Track
import org.fossify.musicplayer.playback.CustomCommands
import org.fossify.musicplayer.playback.PlaybackService
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

class TrackActivity : SimpleControllerActivity(), PlaybackSpeedListener {
    companion object {
        private const val SEEK_COALESCE_MS = 150L
        private const val UPDATE_INTERVAL_MS = 150L
    }

    private var isThirdPartyIntent = false

    private val handler = Handler(Looper.getMainLooper())
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var seekJob: Job? = null
    private var prefetchJob: Job? = null
    private var seekCount = 0

    // Single source of truth for the entire screen
    private var uiState by mutableStateOf(TrackUiState())

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TrackScreen(
                state = uiState,
                onBack = ::finish,
                onSwipeDown = ::handleSwipeDown,
                onPlayPause = ::togglePlayback,
                onPrevious = ::seekToPrevious,
                onNext = ::seekToNext,
                onSeek = ::seekTo,
                onShuffleToggle = ::toggleShuffle,
                onFavoriteToggle = { uiState = uiState.copy(isFavorite = !uiState.isFavorite) },
                onPlaybackSettingToggle = ::togglePlaybackSetting,
                onAddLyrics = { /* TODO: open lyrics editor */ },
                onNextTrackClick = {
                    startActivity(Intent(applicationContext, QueueActivity::class.java))
                },
                onSpeedClick = ::showPlaybackSpeedPicker,
                onSeekToQueueIndex = ::seekToQueueIndex,
            )
        }

        isThirdPartyIntent = intent.action == Intent.ACTION_VIEW
        if (isThirdPartyIntent && (savedInstanceState == null || PlaybackService.currentMediaItem == null)) {
            initThirdPartyIntent()
            return
        }

        refreshTrackInfo()
        updatePlayerState()
    }

    override fun onResume() {
        super.onResume()
        updatePlayerState()
        refreshTrackInfo()
    }

    override fun onPause() {
        super.onPause()
        cancelProgressUpdate()
    }

    override fun onStop() {
        super.onStop()
        cancelProgressUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelProgressUpdate()
        activityScope.cancel()
        if (isThirdPartyIntent && !isChangingConfigurations) {
            withPlayer {
                if (!isReallyPlaying) sendCommand(CustomCommands.CLOSE_PLAYER)
            }
        }
    }

    // ── State population ──────────────────────────────────────────────────────

    private fun refreshTrackInfo() {
        withPlayer {
            applyTrackInfo(currentMediaItem)
            applyNextTrackInfo(nextMediaItem)
            launchPrefetchQueue(this)
        }
    }

    private fun applyTrackInfo(item: MediaItem?) {
        val track = item?.toTrack() ?: return
        uiState = uiState.copy(
            title = track.title,
            artist = track.artist,
            durationSecs = track.duration,
        )
        // Cover art drives both the foreground image and the blurred background (via Modifier.blur)
        getTrackCoverArt(track) { coverArt ->
            runOnUiThread { uiState = uiState.copy(coverArt = coverArt) }
        }
    }

    private fun applyNextTrackInfo(item: MediaItem?) {
        val track = item?.toTrack()
        if (track == null) {
            uiState = uiState.copy(nextTrack = null)
            return
        }
        val artistSuffix = if (track.artist.trim().isNotEmpty() && track.artist != MediaStore.UNKNOWN_STRING) {
            " • ${track.artist}"
        } else ""

        uiState = uiState.copy(
            nextTrack = NextTrackUi(
                label = "${getString(R.string.next_track)} ${track.title}$artistSuffix",
                coverArt = null,
            )
        )
        getTrackCoverArt(track) { coverArt ->
            runOnUiThread {
                uiState = uiState.copy(nextTrack = uiState.nextTrack?.copy(coverArt = coverArt))
            }
        }
    }

    // ── Queue pre-fetching ────────────────────────────────────────────────────

    /**
     * Resolves the full ordered queue and current index from [controller], then kicks off
     * concurrent cover-art pre-fetching. Must be called with a live [MediaController] reference
     * (i.e. inside a [withPlayer] block) so player state is consistent.
     */
    private fun launchPrefetchQueue(controller: MediaController) {
        val resolved = resolveQueue(controller) ?: return
        val (tracks, currentIdx) = resolved

        prefetchJob?.cancel()
        prefetchJob = activityScope.launch {
            // Immediately publish lightweight queue items so the pager has metadata
            val existingCovers = uiState.queueCovers
            val queueItems = tracks.mapIndexed { i, track ->
                QueueTrack(
                    index = i,
                    title = track.title,
                    artist = track.artist,
                    coverArt = existingCovers[i],
                )
            }
            uiState = uiState.copy(queue = queueItems, currentQueueIndex = currentIdx)

            // Fetch covers concurrently, skipping indices already resolved
            val deferred: List<Deferred<Pair<Int, Any?>>> = tracks.mapIndexed { i, track ->
                async(Dispatchers.IO) {
                    val art: Any? = existingCovers[i] ?: fetchCoverArt(track)
                    i to art
                }
            }

            val newCovers: Map<Int, Any?> = existingCovers.toMutableMap().apply {
                putAll(deferred.awaitAll())
            }

            // Rebuild queue with resolved cover art
            val updatedQueue = queueItems.map { it.copy(coverArt = newCovers[it.index]) }
            uiState = uiState.copy(queue = updatedQueue, queueCovers = newCovers)
        }
    }

    /** Suspend wrapper around the callback-based [getTrackCoverArt]. */
    private suspend fun fetchCoverArt(track: Track): Any? =
        suspendCancellableCoroutine { cont ->
            getTrackCoverArt(track) { art -> cont.resume(art) }
        }

    // ── Player state sync ─────────────────────────────────────────────────────

    private fun updatePlayerState() {
        withPlayer {
            val playing = isReallyPlaying
            if (playing) scheduleProgressUpdate() else cancelProgressUpdate()
            uiState = uiState.copy(
                isPlaying = playing,
                progressSecs = currentPosition.milliseconds.inWholeSeconds.toInt(),
                isShuffleOn = shuffleModeEnabled,
                playbackSetting = getPlaybackSetting(repeatMode).toUi(),
            )
        }
    }

    private fun scheduleProgressUpdate() {
        cancelProgressUpdate()
        withPlayer {
            val delay = (UPDATE_INTERVAL_MS / config.playbackSpeed).toLong()
            handler.postDelayed(delay) {
                uiState = uiState.copy(
                    progressSecs = currentPosition.milliseconds.inWholeSeconds.toInt()
                )
                scheduleProgressUpdate()
            }
        }
    }

    private fun cancelProgressUpdate() = handler.removeCallbacksAndMessages(null)

    // ── Player event callbacks ────────────────────────────────────────────────

    override fun onPlaybackStateChanged(playbackState: Int) = updatePlayerState()

    override fun onIsPlayingChanged(isPlaying: Boolean) = updatePlayerState()

    override fun onRepeatModeChanged(repeatMode: Int) {
        if (config.playbackSetting != PlaybackSetting.STOP_AFTER_CURRENT_TRACK) {
            uiState = uiState.copy(playbackSetting = getPlaybackSetting(repeatMode).toUi())
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        uiState = uiState.copy(isShuffleOn = shuffleModeEnabled)
        // Re-fetch queue in new shuffle order — covers are re-used from existing map
        withPlayer { launchPrefetchQueue(this) }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)
        if (mediaItem == null) {
            finish()
        } else {
            uiState = uiState.copy(progressSecs = 0)
            applyTrackInfo(mediaItem)
            // Update next-track info and current queue index inside withPlayer where nextMediaItem is accessible
            withPlayer {
                applyNextTrackInfo(nextMediaItem)
                val newIdx = currentQueueIndexFor(this)
                uiState = uiState.copy(currentQueueIndex = newIdx)
            }
        }
    }

    // ── User actions ──────────────────────────────────────────────────────────

    private fun handleSwipeDown() {
        finish()
        overridePendingTransition(0, org.fossify.commons.R.anim.slide_down)
    }

    private fun toggleShuffle() {
        val enabled = !config.isShuffleEnabled
        config.isShuffleEnabled = enabled
        toast(if (enabled) R.string.shuffle_enabled else R.string.shuffle_disabled)
        uiState = uiState.copy(isShuffleOn = enabled)
        withPlayer {
            shuffleModeEnabled = enabled
            applyNextTrackInfo(nextMediaItem)
            // Queue order changed — re-fetch with existing cover cache
            launchPrefetchQueue(this)
        }
    }

    private fun seekTo(seconds: Int) {
        uiState = uiState.copy(progressSecs = seconds)
        withPlayer { seekTo(seconds * 1000L) }
    }

    private fun seekToQueueIndex(queueIndex: Int) {
        withPlayer {
            val playerIndex = queueIndexToPlayerIndex(this, queueIndex) ?: return@withPlayer
            play()
            seekTo(playerIndex, 0)
        }
    }

    private fun seekToNext() {
        seekCount += 1
        seekWithDelay()
    }

    private fun seekToPrevious() {
        withPlayer {
            if (maybeRestartOnPrevious()) return@withPlayer
            seekCount -= 1
            seekWithDelay()
        }
    }

    private fun seekWithDelay() {
        seekJob?.cancel()
        seekJob = activityScope.launch {
            delay(SEEK_COALESCE_MS)
            if (seekCount != 0) seekByCount(seekCount)
        }
    }

    private fun seekByCount(count: Int) {
        withPlayer {
            if (currentMediaItem == null) return@withPlayer
            val total = mediaItemCount
            val seekIndex = if (shuffleModeEnabled) {
                val shuffled = shuffledMediaItemsIndices.indexOf(currentMediaItemIndex)
                shuffledMediaItemsIndices.getOrNull(rotateIndex(total, shuffled + count)) ?: return@withPlayer
            } else {
                rotateIndex(total, currentMediaItemIndex + count)
            }
            play()
            seekTo(seekIndex, 0)
            seekCount = 0
        }
    }

    private fun togglePlaybackSetting() {
        val next = config.playbackSetting.nextPlaybackOption
        config.playbackSetting = next
        toast(next.descriptionStringRes)
        uiState = uiState.copy(playbackSetting = next.toUi())
        withPlayer { setRepeatMode(next) }
    }

    private fun initThirdPartyIntent() {
        getTrackFromUri(intent.data) { track ->
            runOnUiThread {
                if (track != null) prepareAndPlay(listOf(track), startActivity = false)
                else {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                    finish()
                }
            }
        }
    }

    private fun showPlaybackSpeedPicker() {
        val fragment = PlaybackSpeedFragment()
        fragment.show(supportFragmentManager, PlaybackSpeedFragment::class.java.simpleName)
        fragment.setListener(this)
    }

    override fun updatePlaybackSpeed(speed: Float) {
        withPlayer { setPlaybackSpeed(speed) }
    }

    private fun rotateIndex(total: Int, index: Int): Int = (index % total + total) % total
}

// ── Queue helpers (plain functions — no receiver ambiguity) ───────────────────

/**
 * Returns the ordered list of [Track]s (respecting shuffle) and the position of the
 * currently playing track within that ordered list. Returns null if the player is empty.
 */
private fun resolveQueue(controller: MediaController): Pair<List<Track>, Int>? {
    if (controller.mediaItemCount == 0) return null

    val orderedIndices: List<Int> = if (controller.shuffleModeEnabled) {
        controller.shuffledMediaItemsIndices.takeIf { it.isNotEmpty() }
            ?: (0 until controller.mediaItemCount).toList()
    } else {
        (0 until controller.mediaItemCount).toList()
    }

    val tracks: List<Track> = orderedIndices.mapNotNull { playerIdx ->
        controller.getMediaItemAt(playerIdx).toTrack()
    }

    val currentQueueIdx = orderedIndices.indexOf(controller.currentMediaItemIndex).coerceAtLeast(0)
    return tracks to currentQueueIdx
}

/**
 * Returns the position of the currently playing track within the ordered queue.
 * Mirrors the index logic in [resolveQueue] without rebuilding the full track list.
 */
private fun currentQueueIndexFor(controller: MediaController): Int {
    val orderedIndices: List<Int> = if (controller.shuffleModeEnabled) {
        controller.shuffledMediaItemsIndices.takeIf { it.isNotEmpty() }
            ?: (0 until controller.mediaItemCount).toList()
    } else {
        (0 until controller.mediaItemCount).toList()
    }
    return orderedIndices.indexOf(controller.currentMediaItemIndex).coerceAtLeast(0)
}

/**
 * Maps a queue-order index back to the raw player media-item index for [MediaController.seekTo].
 */
private fun queueIndexToPlayerIndex(controller: MediaController, queueIndex: Int): Int? {
    val orderedIndices: List<Int> = if (controller.shuffleModeEnabled) {
        controller.shuffledMediaItemsIndices.takeIf { it.isNotEmpty() }
            ?: (0 until controller.mediaItemCount).toList()
    } else {
        (0 until controller.mediaItemCount).toList()
    }
    return orderedIndices.getOrNull(queueIndex)
}

// ── Extension: PlaybackSetting → PlaybackSettingUi ────────────────────────────

private fun PlaybackSetting.toUi(): PlaybackSettingUi = when (this) {
    PlaybackSetting.REPEAT_OFF -> PlaybackSettingUi.REPEAT_OFF
    PlaybackSetting.REPEAT_TRACK -> PlaybackSettingUi.REPEAT_ONE
    PlaybackSetting.REPEAT_PLAYLIST -> PlaybackSettingUi.REPEAT_ALL
    PlaybackSetting.STOP_AFTER_CURRENT_TRACK -> PlaybackSettingUi.STOP_AFTER_CURRENT
}
