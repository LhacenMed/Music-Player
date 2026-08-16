package org.fossify.musicplayer.views

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.postDelayed
import androidx.core.view.updatePadding
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.value
import org.fossify.commons.helpers.LOWER_ALPHA
import org.fossify.commons.helpers.MEDIUM_ALPHA
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.R
import org.fossify.musicplayer.activities.SimpleControllerActivity
import org.fossify.musicplayer.databinding.ViewPlaybackPanelBinding
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.currentMediaItems
import org.fossify.musicplayer.extensions.currentMediaItemsShuffled
import org.fossify.musicplayer.extensions.dampen
import org.fossify.musicplayer.extensions.getPlaybackSetting
import org.fossify.musicplayer.extensions.getPlaybackSurfaceColor
import org.fossify.musicplayer.extensions.isReallyPlaying
import org.fossify.musicplayer.extensions.recycler
import org.fossify.musicplayer.extensions.setRepeatMode
import org.fossify.musicplayer.extensions.shuffledMediaItemsIndices
import org.fossify.musicplayer.extensions.systemBarInsetsCompat
import org.fossify.musicplayer.extensions.smoothScrollByPageTo
import org.fossify.musicplayer.extensions.toTrack
import org.fossify.musicplayer.extensions.toTracks
import org.fossify.musicplayer.extensions.viewBinding
import org.fossify.musicplayer.helpers.LyricsExtractor
import org.fossify.musicplayer.helpers.PlaybackSetting
import org.fossify.musicplayer.models.Events
import org.fossify.musicplayer.models.Track
import org.greenrobot.eventbus.EventBus
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/**
 * The full playback UI: a swipeable cover carousel, the playing track's details, a wavy seek bar,
 * the expressive transport controls and — where the screen is tall enough — a synced lyrics strip.
 *
 * Hosted by the playback bottom sheet on every screen that shows it, so all of the player wiring
 * lives here rather than in each host.
 */
class PlaybackPanel(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {
    companion object {
        private const val SEEK_COALESCE_INTERVAL_MS = 150L
        /** Auxio polls the position every 100ms, which is what keeps the seek bar continuous. */
        private const val UPDATE_INTERVAL_MS = 100L
        private const val MS_PER_DECISECOND = 100L
    }

    private val binding by viewBinding(ViewPlaybackPanelBinding::bind)
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default)
    private val coverAdapter = CoverPagerAdapter { performClick() }

    private var pagerCallback: UserAwarePagerCallback? = null
    private var seekJob: Job? = null
    private var seekCount = 0

    /** The carousel move waiting for the panel to settle, consumed by whichever frame applies it. */
    private var pendingPagerCommand: PagerCommand? = null

    /**
     * The page the carousel was last aimed at. Its move is a scroll by a page delta, which is only
     * meaningful from a settled page, and the pager's own current item does not catch up until the
     * scroll ends. Aiming twice at the same page therefore has to be recognised here rather than by
     * asking the pager where it is.
     */
    private var aimedPagerPosition = RecyclerView.NO_POSITION

    /** Guards against a stale lyrics read landing after the track has already moved on. */
    private var lyricsToken = 0

    /** Guards against a stale favorite read landing after the button already says otherwise. */
    private var favoriteToken = 0

    /** Last wave state pushed to the seek bar, so it is only re-armed on a real play/pause flip. */
    private var isWaveEnabled: Boolean? = null

    private fun withPlayer(callback: MediaController.() -> Unit) =
        (context as SimpleControllerActivity).withPlayer(callback)

    fun initialize(
        onNavigateUp: () -> Unit,
        onMoreClick: () -> Unit,
        onEqualizerClick: () -> Unit
    ) = binding.apply {
        // BottomSheetContentBehavior rewrites the bottom system bar inset to however much of the
        // queue sheet is showing, so padding by it is what keeps the transport controls clear of
        // the queue bar instead of being overlapped by it.
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.updatePadding(bottom = insets.systemBarInsetsCompat.bottom)
            insets
        }

        playbackToolbar.setNavigationOnClickListener { onNavigateUp() }
        playbackToolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_open_equalizer) {
                onEqualizerClick()
                true
            } else {
                false
            }
        }
        playbackFavorite.setOnClickListener { toggleFavorite() }
        playbackMore.setOnClickListener { onMoreClick() }

        playbackPager.apply {
            adapter = coverAdapter
            setPageTransformer(CarouselTransformer())
            // Offscreen pages must stay alive for the carousel masking to have anything to mask.
            offscreenPageLimit = 1
            // Otherwise vertical drags meant for the sheet get eaten as horizontal swipes.
            dampen()
            recycler().apply {
                // Make it possible to collapse the bottom sheet from the pager's touch area.
                isNestedScrollingEnabled = false
                overScrollMode = OVER_SCROLL_NEVER
                // All of the carousel's motion belongs to the page transformer. Left to animate,
                // re-seating the queue would cross-fade the outgoing pages over several frames
                // instead of the covers simply being where they belong on the next one.
                itemAnimator = null
            }
            pagerCallback = UserAwarePagerCallback(this) { position ->
                // The swipe settled the carousel here, so this is where it is aimed from now on.
                aimedPagerPosition = position
                withPlayer {
                    val target = playerIndexOf(position) ?: return@withPlayer
                    if (target != currentMediaItemIndex) {
                        play()
                        seekTo(target, 0)
                    }
                }
            }.also { it.attach() }
        }

        playbackSeekBar?.listener = object : StyledSeekBar.Listener {
            override fun onSeekConfirmed(positionDs: Long) {
                withPlayer { seekTo(positionDs * MS_PER_DECISECOND) }
            }
        }

        playbackRepeat.setOnClickListener { togglePlaybackSetting() }
        playbackSkipPrev.setOnClickListener { seekToPrevious() }
        playbackPlayPause.setOnClickListener { (context as SimpleControllerActivity).togglePlayback() }
        playbackSkipNext.setOnClickListener { seekToNext() }
        playbackShuffle.setOnClickListener { toggleShuffle() }

        playbackSong.setOnLongClickListener {
            context.copyToClipboard(playbackSong.value)
            true
        }
        playbackArtist.setOnLongClickListener {
            context.copyToClipboard(playbackArtist.value)
            true
        }

        setupShuffleButton()
        setupPlaybackSettingButton()
    }

    /**
     * Tint the transport row from the app's accent.
     *
     * Auxio gets its play/skip hierarchy from the M3 container roles, but Fossify only ever supplies
     * a single accent and leaves the rest of the palette at the framework defaults, which is why the
     * roles resolve to unrelated colours. Deriving the tints here keeps the play button dominant
     * over the skips and, crucially, holds the icon colour steady across play and pause.
     */
    fun updateColors() {
        val primary = context.getProperPrimaryColor()
        binding.playbackPlayPause.apply {
            backgroundTintList = ColorStateList.valueOf(primary)
            iconTint = ColorStateList.valueOf(primary.getContrastColor())
        }

        binding.playbackSeekBar?.setTrackColors(
            playedColor = primary,
            remainingColor = primary.adjustAlpha(LOWER_ALPHA)
        )

        // The toggles read as outlines on the sheet, so their fill is the sheet's own colour.
        val sheetContainer = ColorStateList.valueOf(context.getPlaybackSurfaceColor())
        arrayOf(binding.playbackRepeat, binding.playbackShuffle).forEach {
            it.backgroundTintList = sheetContainer
        }

        val skipContainer = ColorStateList.valueOf(primary.adjustAlpha(MEDIUM_ALPHA))
        val skipIcon = ColorStateList.valueOf(context.getProperTextColor())
        arrayOf(binding.playbackSkipPrev, binding.playbackSkipNext).forEach {
            it.backgroundTintList = skipContainer
            it.iconTint = skipIcon
        }
    }

    fun release() {
        cancelProgressUpdate()
        pagerCallback?.release()
        pagerCallback = null
    }

    fun updateTrack(item: MediaItem?) {
        val track = item?.toTrack() ?: return
        binding.playbackToolbar.subtitle = context.config.playbackSource
        binding.playbackSong.text = track.title
        binding.playbackArtist.text = track.artist
        binding.playbackSeekBar?.durationDs = track.duration.toLong() * 10

        updateCarousel()
        loadLyrics(track)
        loadFavorite(track)
    }

    fun updateTrackInfo() {
        withPlayer { updateTrack(currentMediaItem) }
    }

    fun updatePlayerState() {
        withPlayer {
            val isPlaying = isReallyPlaying
            if (isPlaying) {
                scheduleProgressUpdate()
            } else {
                cancelProgressUpdate()
            }

            updateProgress(currentPosition)
            updatePlayPause(isPlaying)
            setupShuffleButton(shuffleModeEnabled)
            maybeUpdatePlaybackSettingButton(context.getPlaybackSetting(repeatMode))
        }
    }

    fun updatePlayPause(isPlaying: Boolean) {
        // The icon is a checked-state selector, so the button's state drives the artwork.
        binding.playbackPlayPause.isChecked = isPlaying

        // setWaveEnabled cancels the running spring and resets the phase clock, so it must only be
        // called when the state actually flips. Player callbacks fire repeatedly during a seek, and
        // re-arming the wave on each one is what makes it stutter mid-scrub.
        if (isWaveEnabled != isPlaying) {
            isWaveEnabled = isPlaying
            binding.playbackSeekBar?.setWaveEnabled(isPlaying)
        }
    }

    fun resetProgress() {
        binding.playbackSeekBar?.positionDs = 0
    }

    fun maybeUpdatePlaybackSettingButton(playbackSetting: PlaybackSetting) {
        if (context.config.playbackSetting != PlaybackSetting.STOP_AFTER_CURRENT_TRACK) {
            setupPlaybackSettingButton(playbackSetting)
        }
    }

    fun setupShuffleButton(isShuffleEnabled: Boolean = context.config.isShuffleEnabled) {
        binding.playbackShuffle.apply {
            isChecked = isShuffleEnabled
            contentDescription =
                context.getString(if (isShuffleEnabled) R.string.disable_shuffle else R.string.enable_shuffle)
        }
    }


    fun cancelProgressUpdate() {
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * Read the player once and turn what it holds into a single [PagerCommand] for the carousel.
     *
     * Pages follow the order that skip next/previous actually traverses, so in shuffle mode the
     * carousel is the shuffled queue rather than the underlying media item order.
     */
    fun updateCarousel() = withPlayer {
        val tracks = if (shuffleModeEnabled) {
            currentMediaItemsShuffled.toTracks()
        } else {
            currentMediaItems.toTracks()
        }

        schedulePagerCommand(
            PagerCommand(
                tracks = tracks,
                scrollTo = pagerPositionOf(currentMediaItemIndex),
                // Compared against what the carousel is actually holding rather than a cached copy,
                // so a queue swapped in while this panel was off screen is always caught.
                replacesQueue = tracks != coverAdapter.tracks
            )
        )
    }

    /**
     * Hold [command] until the panel has finished laying out, then apply it.
     *
     * The stacked sheets remeasure on the slightest state change, and moving the pager mid-settle
     * drops enough frames to ruin the skip animation, so the move waits for the frame to commit.
     * A newer command replaces an older one, since only the latest state is worth showing.
     */
    private fun schedulePagerCommand(command: PagerCommand) {
        pendingPagerCommand = command
        binding.playbackPager.apply {
            if (!isAttachedToWindow) {
                post { applyPagerCommand() }
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isHardwareAccelerated) {
                viewTreeObserver.registerFrameCommitCallback {
                    post { postOnAnimation { applyPagerCommand() } }
                }
                postInvalidateOnAnimation()
            } else {
                // Let the current layout happen, then wait for the next one to conclude.
                postOnAnimation { postOnAnimation { applyPagerCommand() } }
            }
        }
    }

    /** Apply the pending command, if an earlier frame has not already consumed it. */
    private fun applyPagerCommand() {
        val command = pendingPagerCommand ?: return
        pendingPagerCommand = null

        // Already going there. A queue swap is exempt, since the same page stands for another
        // track once the pages underneath have been re-seated.
        if (!command.replacesQueue && command.scrollTo == aimedPagerPosition) {
            return
        }

        if (command.replacesQueue) {
            coverAdapter.replace(command.tracks)
        }

        aimedPagerPosition = command.scrollTo
        val delta = binding.playbackPager.currentItem - command.scrollTo
        if (delta == 0) {
            // The pager is already where it belongs, which is the case for a user swipe. Scrolling
            // again here would fight the gesture that caused the track change in the first place.
            return
        }

        if (!command.replacesQueue && abs(delta) == 1) {
            // Adjacent move along the queue already on screen (skip next/previous), which is the
            // only case the drag animation reads well for. Anything further, or a queue that was
            // swapped out underneath, is a jump.
            binding.playbackPager.smoothScrollByPageTo(command.scrollTo)
        } else {
            binding.playbackPager.setCurrentItem(command.scrollTo, false)
        }
    }

    /** Translate a carousel page into the media item index it stands for. */
    private fun MediaController.playerIndexOf(position: Int) = if (shuffleModeEnabled) {
        shuffledMediaItemsIndices.getOrNull(position)
    } else {
        position.takeIf { it in 0 until mediaItemCount }
    }

    /** Translate a media item index into the carousel page that shows it. */
    private fun MediaController.pagerPositionOf(index: Int) = if (shuffleModeEnabled) {
        shuffledMediaItemsIndices.indexOf(index).coerceAtLeast(0)
    } else {
        index
    }

    /**
     * Read the track's lyrics off the main thread. [lyricsToken] drops any read that finishes after
     * the user has already moved to another track.
     */
    private fun loadLyrics(track: Track) {
        val lyricsView = binding.playbackLyrics ?: return
        val token = ++lyricsToken
        lyricsView.update(LyricsState.Loading, 0)

        ensureBackgroundThread {
            val lyrics = LyricsExtractor.extract(track)
            lyricsView.post {
                if (token != lyricsToken) {
                    return@post
                }

                val state = if (lyrics != null) LyricsState.Loaded(lyrics) else LyricsState.Empty
                withPlayer { lyricsView.update(state, currentPosition) }
            }
        }
    }

    /**
     * Bring the heart onto whatever the favorites playlist holds now. The playlist is editable from
     * its own screen too, so the panel cannot assume its own taps are the only thing to fill it.
     */
    fun updateFavorite() = withPlayer {
        val track = currentMediaItem?.toTrack() ?: return@withPlayer
        loadFavorite(track)
    }

    /**
     * Read whether the track sits in the favorites playlist off the main thread. [favoriteToken]
     * drops any read the user has already outrun, by moving on or by tapping the button.
     */
    private fun loadFavorite(track: Track) {
        val favoriteButton = binding.playbackFavorite
        val token = ++favoriteToken

        ensureBackgroundThread {
            val isFavorite = context.audioHelper.isFavorite(track.mediaStoreId)
            favoriteButton.post {
                if (token == favoriteToken) {
                    // The icon is an activated-state selector, so the button's state fills the heart.
                    favoriteButton.isActivated = isFavorite
                }
            }
        }
    }

    /**
     * Move the playing track in or out of the favorites playlist. The heart fills before the write
     * is made, so the tap reads as instant, and the read it invalidates is the one the tap has just
     * answered on the user's behalf.
     */
    private fun toggleFavorite() = withPlayer {
        val track = currentMediaItem?.toTrack() ?: return@withPlayer
        val isFavorite = !binding.playbackFavorite.isActivated
        binding.playbackFavorite.isActivated = isFavorite
        favoriteToken++

        ensureBackgroundThread {
            context.audioHelper.setFavorite(track, isFavorite)
            EventBus.getDefault().post(Events.PlaylistsUpdated())
        }
    }

    private fun toggleShuffle() {
        val isShuffleEnabled = !context.config.isShuffleEnabled
        context.config.isShuffleEnabled = isShuffleEnabled
        context.toast(if (isShuffleEnabled) R.string.shuffle_enabled else R.string.shuffle_disabled)
        setupShuffleButton()
        // Only the request is made here. Enabling shuffle has the service deal a fresh play order,
        // and reading one back before it has been dealt is how the carousel ends up laid out over
        // an order the player has already discarded. The host rebuilds it once the order lands.
        withPlayer { shuffleModeEnabled = isShuffleEnabled }
    }

    private fun togglePlaybackSetting() {
        val newPlaybackSetting = context.config.playbackSetting.nextPlaybackOption
        context.config.playbackSetting = newPlaybackSetting
        context.toast(newPlaybackSetting.descriptionStringRes)
        setupPlaybackSettingButton()
        withPlayer { setRepeatMode(newPlaybackSetting) }
    }

    private fun setupPlaybackSettingButton(playbackSetting: PlaybackSetting = context.config.playbackSetting) {
        binding.playbackRepeat.apply {
            contentDescription = context.getString(playbackSetting.contentDescriptionStringRes)
            setIconResource(playbackSetting.repeatIconRes)
            isChecked = playbackSetting != PlaybackSetting.REPEAT_OFF
        }
    }

    /** The expressive repeat artwork, which distinguishes off/all/one where the tinted icon can't. */
    private val PlaybackSetting.repeatIconRes: Int
        get() = when (this) {
            PlaybackSetting.REPEAT_OFF -> R.drawable.ic_repeat_off_24
            PlaybackSetting.REPEAT_PLAYLIST -> R.drawable.ic_repeat_on_24
            PlaybackSetting.REPEAT_TRACK -> R.drawable.ic_repeat_one_24
            PlaybackSetting.STOP_AFTER_CURRENT_TRACK -> iconRes
        }

    private fun scheduleProgressUpdate() {
        cancelProgressUpdate()
        withPlayer {
            val delayInMillis = (UPDATE_INTERVAL_MS / context.config.playbackSpeed).toLong()
            handler.postDelayed(delayInMillis = delayInMillis) {
                updateProgress(currentPosition)
                scheduleProgressUpdate()
            }
        }
    }

    private fun updateProgress(currentPosition: Long) {
        binding.playbackSeekBar?.positionDs = currentPosition / MS_PER_DECISECOND
        binding.playbackLyrics?.seekTo(currentPosition)
    }

    private fun seekToNext() {
        seekCount += 1
        seekWithDelay()
    }

    private fun seekToPrevious() {
        seekCount -= 1
        seekWithDelay()
    }

    /**
     * This is here so the player can quickly seek next/previous without doing too much work.
     * It probably won't be needed once https://github.com/androidx/media/issues/81 is resolved.
     */
    private fun seekWithDelay() {
        seekJob?.cancel()
        seekJob = scope.launch {
            delay(timeMillis = SEEK_COALESCE_INTERVAL_MS)
            if (seekCount != 0) {
                seekByCount(seekCount)
            }
        }
    }

    private fun seekByCount(count: Int) {
        withPlayer {
            if (currentMediaItem == null) {
                return@withPlayer
            }

            val seekIndex = if (shuffleModeEnabled) {
                val shuffledIndex = shuffledMediaItemsIndices.indexOf(currentMediaItemIndex)
                val seekIndex = rotateIndex(mediaItemCount, shuffledIndex + count)
                shuffledMediaItemsIndices.getOrNull(seekIndex) ?: return@withPlayer
            } else {
                rotateIndex(mediaItemCount, currentMediaItemIndex + count)
            }

            play()
            seekTo(seekIndex, 0)
            seekCount = 0
        }
    }

    private fun rotateIndex(total: Int, index: Int) = (index % total + total) % total
}
