package org.fossify.musicplayer.activities

import android.os.Handler
import android.content.Intent
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.annotation.CallSuper
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.os.bundleOf
import androidx.core.os.postDelayed
import androidx.core.view.isInvisible
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BackportBottomSheetBehavior
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.views.MyRecyclerView
import org.fossify.musicplayer.R
import org.fossify.musicplayer.adapters.BaseMusicAdapter
import org.fossify.musicplayer.dialogs.TrackMenuDialog
import org.fossify.musicplayer.extensions.coordinatorLayoutBehavior
import org.fossify.musicplayer.extensions.getPlaybackSetting
import org.fossify.musicplayer.extensions.getPlaybackSurfaceColor
import org.fossify.musicplayer.extensions.isReallyPlaying
import org.fossify.musicplayer.extensions.sendCommand
import org.fossify.musicplayer.extensions.shuffledMediaItemsIndices
import org.fossify.musicplayer.extensions.toTrack
import org.fossify.musicplayer.extensions.toTracks
import org.fossify.musicplayer.helpers.EXTRA_SHUFFLE_INDICES
import org.fossify.musicplayer.playback.CustomCommands
import org.fossify.musicplayer.playback.PlaybackService
import org.fossify.musicplayer.playback.PlaybackService.Companion.updatePlaybackInfo
import org.fossify.musicplayer.views.CurrentTrackBar
import org.fossify.musicplayer.views.PlaybackBottomSheetBehavior
import org.fossify.musicplayer.views.PlaybackPanel
import org.fossify.musicplayer.views.QueueAdapter
import org.fossify.musicplayer.views.QueueBottomSheetBehavior
import org.fossify.musicplayer.views.QueueDragCallback
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import com.google.android.material.R as MR

/**
 * Base class for activities that host the playback UI: the bar that shows what is playing, the
 * panel it expands into, and the queue sheet stacked inside that. Every screen including
 * `view_playback_sheet` inherits the whole thing by calling [setupPlaybackSheet].
 */
abstract class SimpleMusicActivity : SimpleControllerActivity(), Player.Listener,
    ViewTreeObserver.OnPreDrawListener {
    private companion object {
        const val PROGRESS_INTERVAL_MS = 100L
    }

    private val progressHandler = Handler(Looper.getMainLooper())

    private var elevationNormal = 0f
    private var normalCornerSize = 0f
    private var maxScaleXDistance = 0f
    private var sheetBackCallback: SheetBackPressedCallback? = null
    private var expandPanelWhenReady = false

    /** Set while a freshly dealt shuffle order is on its way back from the service. */
    private var isReshuffling = false

    protected val playbackCoordinator: CoordinatorLayout by lazy { findViewById(R.id.playback_coordinator) }
    protected val sheetContent: View by lazy { findViewById(R.id.playback_content) }
    protected val panel: PlaybackPanel by lazy { findViewById(R.id.playback_panel) }

    private val trackBar: CurrentTrackBar by lazy { findViewById(R.id.current_track_bar) }
    private val sheetScrim: View by lazy { findViewById(R.id.main_sheet_scrim) }
    private val queueContent: View by lazy { findViewById(R.id.queue_content) }
    private val queueDivider: View by lazy { findViewById(R.id.queue_divider) }
    private val playbackSheet: CoordinatorLayout by lazy { findViewById(R.id.playback_sheet) }
    private val queueSheet: View by lazy { findViewById(R.id.queue_sheet) }
    private val queueList: MyRecyclerView by lazy { findViewById(R.id.queue_list) }

    private val playbackSheetBehavior
        get() = playbackSheet.coordinatorLayoutBehavior as PlaybackBottomSheetBehavior

    private val queueSheetBehavior
        get() = queueSheet.coordinatorLayoutBehavior as QueueBottomSheetBehavior

    override fun onResume() {
        super.onResume()
        updateCurrentTrackBar()
        panel.updateColors()
        // The track may well have moved on while this screen was stopped, and no callback for that
        // arrives once it is listening again.
        refreshPlayingTrackIndicator()
    }

    override fun onPause() {
        super.onPause()
        cancelTrackBarProgress()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelTrackBarProgress()
        playbackCoordinator.viewTreeObserver.removeOnPreDrawListener(this)
        panel.release()
    }

    /** Wire up the playback bar, panel and queue sheet that `view_playback_sheet` brought in. */
    protected fun setupPlaybackSheet() {
        playbackSheetBehavior.makeBackgroundDrawable(this)
        normalCornerSize = playbackSheetBehavior.sheetBackgroundDrawable.topLeftCornerResolvedSize
        elevationNormal = resources.getDimension(MR.dimen.m3_sys_elevation_level1)
        maxScaleXDistance = resources.getDimension(MR.dimen.m3_back_progress_bottom_container_max_scale_x_distance)
        playbackSheet.elevation = 0f
        // The scrim is painted in the sheet's own colour, not the content's. Fading it in over the
        // last stretch of the slide turns everything around the sheet — the strip left under the
        // status bar included — the same colour as the sheet, exactly as its corners square off,
        // so the panel arrives reading as the whole screen rather than as a card on top of one.
        sheetScrim.setBackgroundColor(getPlaybackSurfaceColor())

        setupCurrentTrackBar(trackBar)
        // Tapping the bar expands the sheet rather than opening a separate player screen.
        trackBar.setOnClickListener {
            hideKeyboard()
            tryOpenPlaybackPanel()
        }

        panel.initialize(
            onNavigateUp = { tryClosePlaybackPanel() },
            onMoreClick = { showTrackMenu() },
            onEqualizerClick = { startActivity(Intent(applicationContext, EqualizerActivity::class.java)) }
        )

        sheetBackCallback = SheetBackPressedCallback(playbackSheetBehavior, queueSheetBehavior).also {
            onBackPressedDispatcher.addCallback(this, it)
        }

        setupSwipeToStop()

        setupQueueDivider()

        playbackCoordinator.viewTreeObserver.addOnPreDrawListener(this)
    }

    /**
     * Let the playback bar be swiped away, which stops playback rather than just hiding the bar.
     *
     * Everything else only hides the sheet once playback is already over, so a hide arriving while
     * the player still holds a track can only have come from the user's own gesture.
     */
    private fun setupSwipeToStop() {
        playbackSheetBehavior.addBottomSheetCallback(object : BackportBottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BackportBottomSheetBehavior.STATE_HIDDEN && PlaybackService.currentMediaItem != null) {
                    stopPlayback(bottomSheet)
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
    }

    /**
     * End playback and let go of the queue. Dropping the media items is what keeps the bar away:
     * everything that brings it back keys off the player still holding a track.
     */
    private fun stopPlayback(bottomSheet: View) {
        withPlayer {
            clearMediaItems()
            updatePlaybackInfo(this)
            sendCommand(CustomCommands.CLOSE_PLAYER)
        }

        bottomSheet.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        toast(R.string.playback_stopped)
    }

    /**
     * Show a rule along the top of the queue once it has been scrolled off its first entry, so the
     * list reads as passing under the handle rather than floating loose beneath it.
     */
    private fun setupQueueDivider() {
        // The scroll listener alone misses positions that change without a scroll, such as the list
        // being seated on the playing track, so watch for relayouts too.
        queueList.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateQueueDivider() }
        queueList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = updateQueueDivider()
        })
    }

    private fun updateQueueDivider() {
        val layoutManager = queueList.layoutManager as LinearLayoutManager
        queueDivider.isInvisible = layoutManager.findFirstCompletelyVisibleItemPosition() < 1
    }

    private fun setupCurrentTrackBar(trackBar: CurrentTrackBar) {
        trackBar.initialize(
            togglePlayback = ::togglePlayback,
            skipToNext = {
                withPlayer {
                    play()
                    seekToNext()
                }
            }
        )
    }

    private fun updateCurrentTrackBar() {
        withPlayer {
            trackBar.updateColors()
            trackBar.updateCurrentTrack(currentMediaItem)
            trackBar.updateTrackState(isReallyPlaying)
            scheduleTrackBarProgress(isReallyPlaying)
        }
    }

    /**
     * Drive the bar's progress line while something is playing. It lives here rather than in the
     * bar so that every screen hosting it ticks the same way, and so ticking stops with the
     * activity.
     */
    private fun scheduleTrackBarProgress(isPlaying: Boolean) {
        cancelTrackBarProgress()
        if (!isPlaying) {
            withPlayer { trackBar.updateProgress(currentPosition) }
            return
        }

        progressHandler.postDelayed(delayInMillis = PROGRESS_INTERVAL_MS) {
            withPlayer { trackBar.updateProgress(currentPosition) }
            scheduleTrackBarProgress(isPlaying = true)
        }
    }

    private fun cancelTrackBarProgress() = progressHandler.removeCallbacksAndMessages(null)

    private fun showTrackMenu() {
        val track = PlaybackService.currentMediaItem?.toTrack() ?: return
        TrackMenuDialog.show(this, track)
    }

    override fun showPlayer() {
        // Picking a track while nothing was playing leaves the sheet hidden until that track
        // actually lands, and a hidden sheet cannot be expanded. Remember the intent and expand
        // once the sheet is back in play.
        expandPanelWhenReady = true
        tryOpenPlaybackPanel()
    }

    /**
     * Bring the queue sheet in line with what the player is holding. The list is the player's own
     * item order, so a drag or a swipe maps straight onto `moveMediaItem` / `removeMediaItem`.
     *
     * @param followCurrentTrack Whether the list should settle on the playing track. Set whenever
     *   the player moves to a different entry, and left off for edits made from the list itself,
     *   which must not yank the list out from under the user.
     */
    private fun refreshQueue(followCurrentTrack: Boolean = false) {
        withPlayer {
            val adapter = getQueueAdapter() ?: createQueueAdapter()
            // The list is the order the player actually traverses, so it follows the shuffle as it
            // is dealt rather than showing the untouched media item order underneath it.
            val playOrder = playOrderIndices
            val playingPosition = playOrder.indexOf(currentMediaItemIndex).coerceAtLeast(0)
            adapter.submitQueue(playOrder.map { getMediaItemAt(it) }.toTracks())
            adapter.setPosition(playingPosition, isReallyPlaying)
            if (followCurrentTrack) {
                settleQueueOn(playingPosition, adapter.itemCount)
            }
        }
    }

    /**
     * Place the entry at [index] near the top of the queue list, so that opening the queue after a
     * track change already reads from the playing track downwards.
     *
     * The move is immediate rather than animated: the list is off-screen while this runs, and it
     * should look as though it had always been there. An entry that is already fully on screen is
     * left exactly where it is, since the list is showing what it needs to.
     */
    private fun settleQueueOn(index: Int, itemCount: Int) {
        val layoutManager = queueList.layoutManager as LinearLayoutManager
        val firstVisible = layoutManager.findFirstCompletelyVisibleItemPosition()
        val lastVisible = layoutManager.findLastCompletelyVisibleItemPosition()

        when {
            // Nothing laid out to measure against, or the entry sits above the window. Seating it
            // against the top of the list is exactly where it belongs either way.
            firstVisible == RecyclerView.NO_POSITION || lastVisible == RecyclerView.NO_POSITION ||
                index < firstVisible -> queueList.scrollToPosition(index)

            // Scrolling downwards seats the target against the bottom of the list, so aim a
            // window further on and let the entry itself come to rest near the top with the
            // upcoming ones below it.
            index > lastVisible ->
                queueList.scrollToPosition(min(itemCount - 1, index + (lastVisible - firstVisible)))
        }
    }

    private fun createQueueAdapter(): QueueAdapter {
        val adapter = QueueAdapter { position ->
            withPlayer {
                val target = mediaItemIndexOf(position) ?: return@withPlayer
                seekTo(target, 0)
                if (!isReallyPlaying) {
                    play()
                }
            }
        }

        val dragCallback = QueueDragCallback(
            adapter = adapter,
            onMoveTrack = { from, to -> withPlayer { movePlayOrderEntry(from, to) } },
            onRemoveTrack = { at -> withPlayer { mediaItemIndexOf(at)?.let(::removeMediaItem) } }
        )

        adapter.itemTouchHelper = ItemTouchHelper(dragCallback).apply {
            attachToRecyclerView(queueList)
        }

        queueList.adapter = adapter
        return adapter
    }

    private fun getQueueAdapter() = queueList.adapter as? QueueAdapter

    /**
     * The media item indices in the order the player traverses them. With shuffle off this is
     * simply the media item order, so everything downstream reads the same either way.
     */
    private val MediaController.playOrderIndices
        get() = shuffledMediaItemsIndices

    /** Translate a position in the queue list into the media item index it stands for. */
    private fun MediaController.mediaItemIndexOf(position: Int) = playOrderIndices.getOrNull(position)

    /**
     * Move a queue entry from one place in the play order to another.
     *
     * Shuffled, the play order is the shuffle order rather than the media item order, and moving
     * media items around underneath it would leave the list showing something the player would not
     * play. Re-dealing the shuffle order itself is what moves the entry the user actually dragged.
     */
    private fun MediaController.movePlayOrderEntry(from: Int, to: Int) {
        if (!shuffleModeEnabled) {
            moveMediaItem(from, to)
            return
        }

        // Reordered exactly as QueueAdapter.moveItems reorders the rows, so the play order and the
        // list the user just dragged stay the same shape and the player's echo is a no-op.
        val playOrder = playOrderIndices.toMutableList()
        playOrder.add(to, playOrder.removeAt(from))
        sendCommand(
            command = CustomCommands.SET_SHUFFLE_ORDER,
            extras = bundleOf(EXTRA_SHUFFLE_INDICES to playOrder.toIntArray())
        )
    }

    @CallSuper
    override fun onPlayerPrepared(success: Boolean) {
        // Only a genuine preparation seeds a queue worth settling; the call also lands on every
        // resume, where the list must keep whatever position the user left it at.
        refreshQueue(followCurrentTrack = success)
        updateSheetVisibility()
        updateCurrentTrackBar()
        panel.updateTrackInfo()
        panel.updatePlayerState()
    }

    @CallSuper
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        trackBar.updateCurrentTrack(mediaItem)
        updateSheetVisibility()
        refreshQueue(followCurrentTrack = true)
        panel.resetProgress()
        panel.updateTrackInfo()
        refreshPlayingTrackIndicator()
    }

    @CallSuper
    override fun onPlaybackStateChanged(playbackState: Int) = withPlayer {
        trackBar.updateTrackState(isReallyPlaying)
        scheduleTrackBarProgress(isReallyPlaying)
        panel.updatePlayerState()
    }

    @CallSuper
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        trackBar.updateTrackState(isPlaying)
        scheduleTrackBarProgress(isPlaying)
        panel.updatePlayerState()
        refreshPlayingTrackIndicator()
    }

    /**
     * Move the playing indicator onto the track with [trackId] wherever this screen lists tracks.
     * Screens without a track list of their own need nothing here.
     */
    protected open fun onPlayingTrackChanged(trackId: Long, isPlaying: Boolean) {}

    /**
     * Hand every track list on this screen the track the player is on.
     *
     * The track is read from the player rather than from the cached playback info, which the
     * service refreshes on its own schedule and so can still be a track behind at this point.
     */
    protected fun refreshPlayingTrackIndicator() = withPlayer {
        val trackId = currentMediaItem?.toTrack()?.mediaStoreId ?: BaseMusicAdapter.NO_PLAYING_TRACK
        onPlayingTrackChanged(trackId, isReallyPlaying)
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        panel.maybeUpdatePlaybackSettingButton(getPlaybackSetting(repeatMode))
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        panel.setupShuffleButton(shuffleModeEnabled)
        // Switching shuffle off restores the media item order, which is already here. Switching it
        // on has the service deal a fresh order that only arrives with the timeline behind this
        // callback, so nothing is rebuilt until then rather than laid out twice over two orders.
        isReshuffling = shuffleModeEnabled
        if (!shuffleModeEnabled) {
            refreshPlayOrder(followCurrentTrack = true)
        }
    }

    @CallSuper
    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        // The other reason a timeline arrives is the player having learned more about the items it
        // already holds, a track's real duration above all. The play order is untouched by that,
        // and rebuilding on it lands a second, identical move on a carousel already making one.
        if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
            return
        }

        refreshPlayOrder(followCurrentTrack = isReshuffling)
        isReshuffling = false
    }

    /**
     * Bring everything that renders the play order onto the order the player is holding now.
     *
     * The queue list and the panel's cover carousel are two views of one thing, so they are only
     * ever rebuilt together, from the same event. Left to refresh on their own schedules, one of
     * them ends up laid out over an order the player has already replaced — pages that show one
     * track and play another.
     */
    private fun refreshPlayOrder(followCurrentTrack: Boolean) {
        refreshQueue(followCurrentTrack = followCurrentTrack)
        panel.updateCarousel()
    }

    private fun updateSheetVisibility() {
        if (PlaybackService.currentMediaItem == null) {
            tryHideAllSheets()
            return
        }

        tryShowSheets()
        if (expandPanelWhenReady) {
            expandPanelWhenReady = false
            // tryShowSheets() has only just moved the sheet to collapsed, so let that settle
            // before expanding or the two state changes cancel each other out.
            playbackSheet.post { tryOpenPlaybackPanel() }
        }
    }

    /**
     * This is where all of the cross-sheet transitions are driven. CoordinatorLayout is overloaded
     * far too much here to rely on its usual listener functionality, so every transition is simply
     * recomputed before each draw.
     */
    override fun onPreDraw(): Boolean {
        val playbackRatio = max(playbackSheetBehavior.calculateSlideOffset(), 0f)
        val playbackOutRatio = 1 - min(playbackRatio * 2, 1f)
        val playbackInRatio = max(playbackRatio - 0.5f, 0f) * 2

        val playbackMaxXScaleDelta = maxScaleXDistance / playbackSheet.width
        val playbackEdgeRatio = max(playbackRatio - 0.9f, 0f) / 0.1f
        val playbackBackRatio = max(1 - ((1 - playbackSheet.scaleX) / playbackMaxXScaleDelta), 0f)
        val playbackLastStretchRatio = min(playbackEdgeRatio * playbackBackRatio, 1f)
        sheetScrim.alpha = playbackLastStretchRatio

        playbackSheetBehavior.sheetBackgroundDrawable.setCornerSize(normalCornerSize * (1 - playbackLastStretchRatio))
        sheetContent.isInvisible = playbackLastStretchRatio == 1f
        playbackSheet.translationZ = (1 - playbackLastStretchRatio) * elevationNormal

        val queueRatio = max(queueSheetBehavior.calculateSlideOffset(), 0f)
        val queueInRatio = max(queueRatio - 0.5f, 0f) * 2

        val queueMaxXScaleDelta = maxScaleXDistance / queueSheet.width
        val queueBackRatio = max(1 - ((1 - queueSheet.scaleX) / queueMaxXScaleDelta), 0f)
        val queueEdgeRatio = max(queueRatio - 0.9f, 0f) / 0.1f

        val queueBarEdgeRatio = max(queueEdgeRatio - 0.5f, 0f) * 2
        val queueBarBackRatio = max(queueBackRatio - 0.5f, 0f) * 2
        val queueBarRatio = min(queueBarEdgeRatio * queueBarBackRatio, 1f)

        val queuePanelEdgeRatio = min(queueEdgeRatio * 2, 1f)
        val queuePanelBackRatio = min(queueBackRatio * 2, 1f)
        val queuePanelRatio = 1 - min(queuePanelEdgeRatio * queuePanelBackRatio, 1f)

        trackBar.alpha = max(playbackOutRatio, queueBarRatio)
        panel.alpha = min(playbackInRatio, queuePanelRatio)
        queueContent.alpha = queueInRatio

        if (PlaybackService.currentMediaItem != null) {
            // Playback sheet intercepts queue sheet touch events, prevent that from occurring by
            // disabling dragging whenever the queue sheet is expanded.
            playbackSheetBehavior.isDraggable =
                queueSheetBehavior.state == BackportBottomSheetBehavior.STATE_COLLAPSED
        }

        // Prevent interactions when a view fully fades out.
        trackBar.isInvisible = trackBar.alpha == 0f
        panel.isInvisible = panel.alpha == 0f

        queueSheet.apply {
            // Queue sheet (not queue content) should fade out with the playback panel.
            alpha = playbackInRatio
            isInvisible = alpha == 0f
        }

        queueContent.isInvisible = queueContent.alpha == 0f

        if (PlaybackService.currentMediaItem == null) {
            // Sometimes lingering drags can un-hide the playback sheet even when we intend to hide
            // it, make sure we keep it hidden.
            tryHideAllSheets()
        }

        sheetBackCallback?.invalidateEnabled()

        return true
    }

    fun tryOpenPlaybackPanel() {
        if (playbackSheetBehavior.targetState == BackportBottomSheetBehavior.STATE_COLLAPSED) {
            // Playback sheet is not expanded and not hidden, we can expand it.
            playbackSheetBehavior.state = BackportBottomSheetBehavior.STATE_EXPANDED
            return
        }

        if (playbackSheetBehavior.state == BackportBottomSheetBehavior.STATE_EXPANDED &&
            queueSheetBehavior.targetState == BackportBottomSheetBehavior.STATE_EXPANDED
        ) {
            // Queue sheet and playback sheet is expanded, close the queue sheet so the playback
            // panel can be shown.
            queueSheetBehavior.state = BackportBottomSheetBehavior.STATE_COLLAPSED
        }
    }

    private fun tryClosePlaybackPanel() {
        if (playbackSheetBehavior.targetState == BackportBottomSheetBehavior.STATE_EXPANDED) {
            // Playback sheet (and possibly queue) needs to be collapsed.
            playbackSheetBehavior.state = BackportBottomSheetBehavior.STATE_COLLAPSED
            queueSheetBehavior.state = BackportBottomSheetBehavior.STATE_COLLAPSED
        }
    }

    private fun tryShowSheets() {
        if (playbackSheetBehavior.targetState == BackportBottomSheetBehavior.STATE_HIDDEN) {
            // Queue sheet behavior is either collapsed or expanded, no hiding needed
            queueSheetBehavior.isDraggable = true
            playbackSheetBehavior.apply {
                // Make sure the view is draggable, at least until the draw checks kick in.
                isDraggable = true
                state = BackportBottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }

    private fun tryHideAllSheets() {
        if (playbackSheetBehavior.targetState != BackportBottomSheetBehavior.STATE_HIDDEN) {
            // Make both bottom sheets non-draggable so the user can't halt the hiding event.
            queueSheetBehavior.apply {
                isDraggable = false
                state = BackportBottomSheetBehavior.STATE_COLLAPSED
            }

            playbackSheetBehavior.apply {
                isDraggable = false
                state = BackportBottomSheetBehavior.STATE_HIDDEN
            }
        }
    }

    private class SheetBackPressedCallback(
        private val playbackSheetBehavior: PlaybackBottomSheetBehavior<*>,
        private val queueSheetBehavior: QueueBottomSheetBehavior<*>
    ) : OnBackPressedCallback(false) {
        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            if (queueSheetShown()) {
                queueSheetBehavior.startBackProgress(backEvent)
            }

            if (playbackSheetShown()) {
                playbackSheetBehavior.startBackProgress(backEvent)
            }
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            if (queueSheetShown()) {
                queueSheetBehavior.updateBackProgress(backEvent)
                return
            }

            if (playbackSheetShown()) {
                playbackSheetBehavior.updateBackProgress(backEvent)
            }
        }

        override fun handleOnBackPressed() {
            if (queueSheetShown()) {
                queueSheetBehavior.handleBackInvoked()
                return
            }

            if (playbackSheetShown()) {
                playbackSheetBehavior.handleBackInvoked()
            }
        }

        override fun handleOnBackCancelled() {
            if (queueSheetShown()) {
                queueSheetBehavior.cancelBackProgress()
                return
            }

            if (playbackSheetShown()) {
                playbackSheetBehavior.cancelBackProgress()
            }
        }

        fun invalidateEnabled() {
            isEnabled = queueSheetShown() || playbackSheetShown()
        }

        private fun playbackSheetShown() =
            playbackSheetBehavior.targetState != BackportBottomSheetBehavior.STATE_COLLAPSED &&
                playbackSheetBehavior.targetState != BackportBottomSheetBehavior.STATE_HIDDEN

        private fun queueSheetShown() =
            playbackSheetBehavior.state == BackportBottomSheetBehavior.STATE_EXPANDED &&
                queueSheetBehavior.targetState != BackportBottomSheetBehavior.STATE_COLLAPSED
    }
}
