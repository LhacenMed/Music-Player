package org.fossify.musicplayer.activities

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.graphics.scale
import androidx.core.os.postDelayed
import androidx.media3.common.MediaItem
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.fossify.commons.extensions.realScreenSize
import org.fossify.commons.extensions.toast
import org.fossify.musicplayer.R
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.getPlaybackSetting
import org.fossify.musicplayer.extensions.getTrackCoverArt
import org.fossify.musicplayer.extensions.getTrackFromUri
import org.fossify.musicplayer.extensions.isReallyPlaying
import org.fossify.musicplayer.extensions.maybeRestartOnPrevious
import org.fossify.musicplayer.extensions.nextMediaItem
import org.fossify.musicplayer.extensions.sendCommand
import org.fossify.musicplayer.extensions.setRepeatMode
import org.fossify.musicplayer.extensions.shuffledMediaItemsIndices
import org.fossify.musicplayer.extensions.toTrack
import org.fossify.musicplayer.fragments.PlaybackSpeedFragment
import org.fossify.musicplayer.helpers.PlaybackSetting
import org.fossify.musicplayer.interfaces.PlaybackSpeedListener
import org.fossify.musicplayer.models.Track
import org.fossify.musicplayer.playback.CustomCommands
import org.fossify.musicplayer.playback.PlaybackService
import kotlin.time.Duration.Companion.milliseconds

class TrackActivity : SimpleControllerActivity(), PlaybackSpeedListener {
    companion object {
        private const val SEEK_COALESCE_MS = 150L
        private const val UPDATE_INTERVAL_MS = 150L
        private const val BG_BLUR_RADIUS = 25f
        private const val BG_SAMPLE_SIZE = 4
    }

    private var isThirdPartyIntent = false

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default)
    private var seekJob: Job? = null
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
        }
    }

    private fun applyTrackInfo(item: MediaItem?) {
        val track = item?.toTrack() ?: return
        uiState = uiState.copy(
            title = track.title,
            artist = track.artist,
            durationSecs = track.duration,
        )
        loadCoverArt(track)
        loadBlurredBackground(track)
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

    private fun loadCoverArt(track: Track) {
        getTrackCoverArt(track) { coverArt ->
            runOnUiThread { uiState = uiState.copy(coverArt = coverArt) }
        }
    }

    private fun loadBlurredBackground(track: Track) {
        getTrackCoverArt(track) { coverArt ->
            scope.launch(Dispatchers.IO) {
                try {
                    val screenW = realScreenSize.x
                    val screenH = realScreenSize.y
                    val raw = Glide.with(this@TrackActivity)
                        .asBitmap()
                        .load(coverArt)
                        .submit(screenW / BG_SAMPLE_SIZE, screenH / BG_SAMPLE_SIZE)
                        .get()
                    val blurred = blurBitmap(raw, BG_BLUR_RADIUS).scale(screenW, screenH, false)
                    launch(Dispatchers.Main) { uiState = uiState.copy(blurredBg = blurred) }
                } catch (_: Exception) { /* dark fallback already shown */ }
            }
        }
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
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)
        if (mediaItem == null) finish()
        else {
            uiState = uiState.copy(progressSecs = 0)
            refreshTrackInfo()
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
        }
    }

    private fun seekTo(seconds: Int) {
        uiState = uiState.copy(progressSecs = seconds)
        withPlayer { seekTo(seconds * 1000L) }
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
        seekJob = scope.launch {
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

    // ── Stack blur (pure Kotlin, no RenderScript) ─────────────────────────────

    private fun blurBitmap(src: Bitmap, radius: Float): Bitmap {
        val r = radius.toInt().coerceIn(1, 25)
        val w = src.width; val h = src.height
        val pix = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }

        val div = r + r + 1; val r1 = r + 1
        val dv = IntArray(256 * (r1 * (r1 + 1) / 2)) { it / (r1 * (r1 + 1) / 2) }
        val stack = Array(div) { IntArray(3) }
        val vmin = IntArray(maxOf(w, h)); val vmax = IntArray(maxOf(w, h))

        // horizontal
        var yi = 0
        for (row in 0 until h) {
            var rs = 0; var gs = 0; var bs = 0
            for (dx in -r..r) {
                val p = pix[yi + dx.coerceIn(0, w - 1)]; val si = dx + r
                stack[si][0] = (p shr 16) and 0xff; stack[si][1] = (p shr 8) and 0xff; stack[si][2] = p and 0xff
                val wt = r1 - Math.abs(dx); rs += stack[si][0] * wt; gs += stack[si][1] * wt; bs += stack[si][2] * wt
            }
            var sp = r
            for (col in 0 until w) {
                pix[yi + col] = -0x1000000 or (dv[rs] shl 16) or (dv[gs] shl 8) or dv[bs]
                if (row == 0) { vmin[col] = minOf(col + r1, w - 1); vmax[col] = maxOf(col - r, 0) }
                val sp1 = (sp + 1) % div
                val pIn = pix[yi + vmin[col]]; val pOut = pix[yi + vmax[col]]
                rs += ((pIn shr 16) and 0xff) - stack[sp1][0]; stack[sp1][0] = (pIn shr 16) and 0xff
                gs += ((pIn shr 8) and 0xff) - stack[sp1][1]; stack[sp1][1] = (pIn shr 8) and 0xff
                bs += (pIn and 0xff) - stack[sp1][2]; stack[sp1][2] = pIn and 0xff
                rs -= stack[sp][0] - ((pOut shr 16) and 0xff)
                gs -= stack[sp][1] - ((pOut shr 8) and 0xff)
                bs -= stack[sp][2] - (pOut and 0xff)
                sp = sp1
            }
            yi += w
        }

        // vertical
        for (col in 0 until w) {
            var rs = 0; var gs = 0; var bs = 0; var yp = -r * w
            for (dy in -r..r) {
                val yi2 = maxOf(0, yp) + col; val si = dy + r
                stack[si][0] = (pix[yi2] shr 16) and 0xff; stack[si][1] = (pix[yi2] shr 8) and 0xff; stack[si][2] = pix[yi2] and 0xff
                val wt = r1 - Math.abs(dy); rs += stack[si][0] * wt; gs += stack[si][1] * wt; bs += stack[si][2] * wt
                yp += w
            }
            var yi2 = col; var sp = r
            for (row in 0 until h) {
                pix[yi2] = -0x1000000 or (dv[rs] shl 16) or (dv[gs] shl 8) or dv[bs]
                if (col == 0) { vmin[row] = minOf(row + r1, h - 1) * w; vmax[row] = maxOf(row - r, 0) * w }
                val sp1 = (sp + 1) % div
                val pIn = pix[vmin[row] + col]; val pOut = pix[vmax[row] + col]
                rs += ((pIn shr 16) and 0xff) - stack[sp1][0]; stack[sp1][0] = (pIn shr 16) and 0xff
                gs += ((pIn shr 8) and 0xff) - stack[sp1][1]; stack[sp1][1] = (pIn shr 8) and 0xff
                bs += (pIn and 0xff) - stack[sp1][2]; stack[sp1][2] = pIn and 0xff
                rs -= stack[sp][0] - ((pOut shr 16) and 0xff)
                gs -= stack[sp][1] - ((pOut shr 8) and 0xff)
                bs -= stack[sp][2] - (pOut and 0xff)
                sp = sp1; yi2 += w
            }
        }

        return Bitmap.createBitmap(pix, w, h, Bitmap.Config.ARGB_8888)
    }
}

// ── Extension: PlaybackSetting → PlaybackSettingUi ────────────────────────────

private fun PlaybackSetting.toUi(): PlaybackSettingUi = when (this) {
    PlaybackSetting.REPEAT_OFF -> PlaybackSettingUi.REPEAT_OFF
    PlaybackSetting.REPEAT_TRACK -> PlaybackSettingUi.REPEAT_ONE
    PlaybackSetting.REPEAT_PLAYLIST -> PlaybackSettingUi.REPEAT_ALL
    PlaybackSetting.STOP_AFTER_CURRENT_TRACK -> PlaybackSettingUi.STOP_AFTER_CURRENT
}
