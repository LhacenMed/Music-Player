package org.fossify.musicplayer.adapters

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.view.Menu
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.extensions.beInvisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.musicplayer.R
import org.fossify.musicplayer.activities.SimpleControllerActivity
import org.fossify.musicplayer.dialogs.TrackMenuDialog
import org.fossify.musicplayer.extensions.*
import org.fossify.musicplayer.helpers.TagHelper
import org.fossify.musicplayer.models.Track
import org.fossify.musicplayer.playback.PlaybackService
import org.fossify.musicplayer.views.CoverFallbackDrawable

abstract class BaseMusicAdapter<Type>(
    var items: ArrayList<Type>,
    activity: BaseSimpleActivity,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    val context = activity as SimpleControllerActivity

    var textToHighlight = ""
    val tagHelper by lazy { TagHelper(context) }
    var placeholder = resources.getSmallPlaceholder(textColor)
    var placeholderBig = resources.getBiggerPlaceholder(textColor)

    /** Auxio's fallback, used wherever a track's own artwork is missing. */
    val trackPlaceholder: Drawable by lazy { CoverFallbackDrawable(context) }

    // Seeded from what the player is already doing, so a list built while something is playing
    // renders the indicator on its very first bind rather than waiting for the next track change.
    private var playingTrackId = PlaybackService.currentMediaItem?.toTrack()?.mediaStoreId ?: NO_PLAYING_TRACK

    /** Whether playback is ongoing, which decides if the playing row's cover animates or rests. */
    protected var isPlaybackOngoing = PlaybackService.isPlaying
        private set
    open val cornerRadius by lazy { resources.getDimension(R.dimen.corner_radius_small).toInt() }

    init {
        setupDragListener(true)
    }

    override fun getItemCount() = items.size

    override fun getSelectableItemCount() = items.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = items.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = items.indexOfFirst { it.hashCode() == key }

    override fun prepareActionMode(menu: Menu) {}

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    open fun getSelectedTracks(): List<Track> = getSelectedItems().filterIsInstance<Track>().toList()

    open fun getAllSelectedTracks(): List<Track> = getSelectedTracks()

    open fun getSelectedItems(): List<Type> {
        return items.filter { selectedKeys.contains(it.hashCode()) }.toList()
    }

    /** True if [item] is the track the player is currently on. */
    fun isPlayingTrack(item: Type) = item is Track && item.mediaStoreId == playingTrackId

    /**
     * Hand a row's overflow button the track it stands for, and the tracks around it that Play and
     * Shuffle act on.
     *
     * The button steps aside once rows are being selected, leaving the trailing edge to the drag
     * handle, so the row never carries two controls at once. It keeps its space rather than
     * collapsing, since the title is measured against it and must stay clear of whatever is there.
     */
    protected fun MaterialButton.setupTrackMenu(track: Track, queue: List<Track>) {
        val activity = this@BaseMusicAdapter.context
        beInvisibleIf(selectedKeys.isNotEmpty())
        iconTint = ColorStateList.valueOf(textColor)
        contentDescription = activity.getString(R.string.more_options_for, track.title)
        setOnClickListener { TrackMenuDialog.showForTrack(activity, track, queue) }
    }

    /** The tracks in this list, which is what a row's Play and Shuffle options traverse. */
    protected fun trackQueue() = items.filterIsInstance<Track>()

    /**
     * Point the playing indicator at the track with [trackId], or clear it with [NO_PLAYING_TRACK].
     *
     * There are two states here, as in Auxio: which row the player is on, which that row marks as
     * selected and accents, and whether playback is ongoing, which decides whether that row's cover
     * animates or rests. Only the rows either state actually touches are repainted, so this stays
     * cheap enough to run on every track change however long the list is.
     */
    fun setPlayingTrack(trackId: Long, isPlaying: Boolean) {
        var movedTrack = false
        if (playingTrackId != trackId) {
            val previousTrackId = playingTrackId
            playingTrackId = trackId
            notifyPlayingIndicatorChanged(previousTrackId)
            notifyPlayingIndicatorChanged(trackId)
            movedTrack = true
        }

        if (isPlaybackOngoing != isPlaying) {
            isPlaybackOngoing = isPlaying
            // The row was repainted just above if the track moved, so only the case where it
            // stayed put is left to handle.
            if (!movedTrack) {
                notifyPlayingIndicatorChanged(trackId)
            }
        }
    }

    /** Repaints every row standing for [trackId], since one track can repeat within a playlist. */
    private fun notifyPlayingIndicatorChanged(trackId: Long) {
        items.forEachIndexed { position, item ->
            if (item is Track && item.mediaStoreId == trackId) {
                notifyItemChanged(position, PAYLOAD_PLAYING_INDICATOR)
            }
        }
    }

    open fun updateItems(newItems: ArrayList<Type>, highlightText: String = "", forceUpdate: Boolean = false) {
        if (forceUpdate || newItems.hashCode() != items.hashCode()) {
            items = newItems.clone() as ArrayList<Type>
            textToHighlight = highlightText
            notifyDataChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataChanged()
        }
    }

    fun shouldShowPlayNext(): Boolean {
        if (!isOneItemSelected()) {
            return false
        }

        val currentMedia = PlaybackService.currentMediaItem ?: return false
        val selectedTrack = getSelectedTracks().firstOrNull()
        return selectedTrack != null && !currentMedia.isSameMedia(selectedTrack)
    }

    fun shouldShowRename(): Boolean {
        if (!isOneItemSelected()) {
            return false
        }

        val selectedTrack = getSelectedTracks().firstOrNull() ?: return false
        return !selectedTrack.path.startsWith("content://") && tagHelper.isEditTagSupported(selectedTrack)
    }

    fun addToQueue() {
        ensureBackgroundThread {
            val allSelectedTracks = getAllSelectedTracks()
            context.runOnUiThread {
                context.addTracksToQueue(allSelectedTracks) {
                    finishActMode()
                }
            }
        }
    }

    fun playNextInQueue() {
        ensureBackgroundThread {
            getSelectedTracks().firstOrNull()?.let { selectedTrack ->
                context.runOnUiThread {
                    context.playNextInQueue(selectedTrack) {
                        finishActMode()
                    }
                }
            }
        }
    }

    fun addToPlaylist() {
        ensureBackgroundThread {
            val allSelectedTracks = getAllSelectedTracks()
            context.runOnUiThread {
                context.addTracksToPlaylist(allSelectedTracks) {
                    finishActMode()
                    notifyDataChanged()
                }
            }
        }
    }

    fun shareFiles() {
        ensureBackgroundThread {
            context.shareFiles(getAllSelectedTracks())
        }
    }

    fun showProperties() {
        ensureBackgroundThread {
            val selectedTracks = getAllSelectedTracks()
            if (selectedTracks.isEmpty()) {
                return@ensureBackgroundThread
            }

            context.runOnUiThread {
                context.showTrackProperties(selectedTracks)
            }
        }
    }

    fun loadImage(imageView: ImageView, resource: Any?, placeholder: Drawable) {
        val options = RequestOptions()
            .error(placeholder)
            .transform(CenterCrop(), RoundedCorners(cornerRadius))

        context.ensureActivityNotDestroyed {
            Glide.with(context)
                .load(resource)
                .apply(options)
                .into(imageView)
        }
    }

    fun updateColors(newTextColor: Int) {
        if (textColor != newTextColor || properPrimaryColor != context.getProperPrimaryColor()) {
            updateTextColor(newTextColor)
            updatePrimaryColor()
            placeholder = resources.getSmallPlaceholder(textColor)
            placeholderBig = resources.getBiggerPlaceholder(textColor)
            notifyDataChanged()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun notifyDataChanged() = if (itemCount == 0) {
        notifyDataSetChanged()
    } else {
        notifyItemRangeChanged(0, itemCount)
    }

    companion object {
        /** Stands for "nothing is playing", so no row carries the indicator. */
        const val NO_PLAYING_TRACK = -1L

        /** Marks a rebind that only has to move the playing indicator, leaving the cover alone. */
        val PAYLOAD_PLAYING_INDICATOR = Any()
    }
}
