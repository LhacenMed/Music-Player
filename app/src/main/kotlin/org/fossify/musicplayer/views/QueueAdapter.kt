package org.fossify.musicplayer.views

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.shape.MaterialShapeDrawable
import org.fossify.musicplayer.databinding.ItemQueueTrackBinding
import org.fossify.musicplayer.extensions.getLiftedQueueSurfaceColor
import org.fossify.musicplayer.extensions.getQueueSurfaceColor
import org.fossify.musicplayer.extensions.getTrackCoverArt
import org.fossify.musicplayer.models.Track

/**
 * Shows the player's queue as an editable list: already-played tracks are dimmed, the playing track
 * is highlighted, and any track can be dragged by its handle or swiped away.
 *
 * @param onTrackClick Called when a queue entry is tapped.
 */
class QueueAdapter(private val onTrackClick: (Int) -> Unit) :
    RecyclerView.Adapter<QueueTrackViewHolder>() {
    private var tracks: List<Track> = emptyList()

    // The playing indicator is tracked by index rather than by item, since the same track can
    // appear at several points in a queue.
    private var currentIndex = 0
    private var isPlaying = false

    /** Started by the drag handle rather than by a long press, matching Auxio. */
    var itemTouchHelper: ItemTouchHelper? = null

    override fun getItemCount() = tracks.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = QueueTrackViewHolder(
        ItemQueueTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: QueueTrackViewHolder, position: Int) {
        holder.bind(tracks[position], onTrackClick, itemTouchHelper)
        holder.isFuture = position > currentIndex
        holder.updatePlayingIndicator(position == currentIndex, isPlaying)
    }

    override fun onBindViewHolder(holder: QueueTrackViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.contains(PAYLOAD_UPDATE_POSITION)) {
            holder.isFuture = position > currentIndex
            holder.updatePlayingIndicator(position == currentIndex, isPlaying)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    /**
     * Replace the queue with [newTracks], re-seating every row.
     *
     * Bailing out on an unchanged queue is what keeps a drag alive: the player echoes our own
     * reorder straight back here, and re-seating the rows would recycle the holder the user is
     * still holding. Past that check the order has genuinely changed, and a reshuffle moves every
     * row at once — far too much for a diff to describe as anything but a rebuild.
     */
    fun submitQueue(newTracks: List<Track>) {
        if (newTracks == tracks) {
            return
        }

        val removed = tracks.size
        notifyItemRangeRemoved(0, removed)
        tracks = newTracks
        notifyItemRangeInserted(0, newTracks.size)
    }

    /**
     * Set the position of the currently playing item. This marks that item as playing and every
     * item before it as already played.
     */
    fun setPosition(index: Int, isPlaying: Boolean) {
        val lastIndex = currentIndex
        currentIndex = index
        this.isPlaying = isPlaying

        // Every item up to whichever index is later has to be repainted, since the played/upcoming
        // split moved along with the playing one.
        val changedThrough = maxOf(lastIndex, currentIndex)
        notifyItemRangeChanged(0, changedThrough + 1, PAYLOAD_UPDATE_POSITION)
    }

    /** Reorder locally so the drag reads as continuous; the player is updated by the caller. */
    fun moveItems(from: Int, to: Int) {
        tracks = tracks.toMutableList().apply { add(to, removeAt(from)) }
        notifyItemMoved(from, to)
    }

    /** Remove locally so the swipe reads as continuous; the player is updated by the caller. */
    fun removeItem(at: Int) {
        tracks = tracks.toMutableList().apply { removeAt(at) }
        notifyItemRemoved(at)
    }

    private companion object {
        val PAYLOAD_UPDATE_POSITION = Any()
    }
}

/** A queue entry that can be re-ordered and removed. */
class QueueTrackViewHolder(private val binding: ItemQueueTrackBinding) :
    RecyclerView.ViewHolder(binding.root), MaterialDragCallback.ViewHolder {
    override val enabled = true
    override val root = binding.root
    override val body = binding.body
    override val delete = binding.background

    override val liftableBackground: Drawable =
        MaterialShapeDrawable.createWithElevationOverlay(binding.root.context).apply {
            // A lifted row reads one step above the queue sheet it is being dragged over.
            fillColor = ColorStateList.valueOf(binding.root.context.getLiftedQueueSurfaceColor())
            alpha = 0
        }

    override val roundableBackground: Drawable =
        MaterialShapeDrawable.createWithElevationOverlay(binding.root.context).apply {
            fillColor = ColorStateList.valueOf(binding.root.context.getQueueSurfaceColor())
        }

    /**
     * Whether this entry is still upcoming, and so shown at full opacity, or already played and
     * therefore greyed out.
     */
    var isFuture: Boolean
        get() = binding.trackCover.isEnabled
        set(value) {
            binding.trackCover.isEnabled = value
            binding.trackName.isEnabled = value
            binding.trackInfo.isEnabled = value
        }

    init {
        binding.body.background = LayerDrawable(arrayOf(roundableBackground, liftableBackground))
    }

    @SuppressLint("ClickableViewAccessibility")
    fun bind(track: Track, onTrackClick: (Int) -> Unit, itemTouchHelper: ItemTouchHelper?) {
        val context = binding.root.context
        binding.trackName.text = track.title
        binding.trackInfo.text = track.artist
        binding.interactBody.setOnClickListener { onTrackClick(bindingAdapterPosition) }

        binding.trackDragHandle.setOnTouchListener { handle, event ->
            handle.performClick()
            // Claiming the press hands the whole gesture to the drag helper, which is also what
            // keeps the handle from rippling: it never reaches the button's own pressed state.
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                itemTouchHelper?.startDrag(this)
                true
            } else {
                false
            }
        }

        context.getTrackCoverArt(track) { coverArt ->
            binding.trackCover.bind(coverArt)
        }

        // Not swiping this holder if it is being re-bound, so make sure the scrim is hidden.
        binding.background.isInvisible = true
    }

    /**
     * Mark this entry as the one the player is on, which hands its cover over to the equalizer.
     * Selecting the row is what drives it, so the name tints along with it through the same state.
     */
    fun updatePlayingIndicator(isActive: Boolean, isPlaying: Boolean) {
        binding.interactBody.isSelected = isActive
        binding.trackCover.setPlaying(isPlaying)
    }
}
