package org.fossify.musicplayer.views

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import org.fossify.musicplayer.databinding.ItemCoverBinding
import org.fossify.musicplayer.extensions.getTrackCoverArt
import org.fossify.musicplayer.models.Track

/**
 * A [RecyclerView.Adapter] that hosts [CoverViewHolder]s containing a [Track]'s cover, one per page
 * of the playback panel's cover carousel.
 *
 * @param onCoverClick Called when a cover is clicked.
 */
class CoverPagerAdapter(private val onCoverClick: () -> Unit) :
    RecyclerView.Adapter<CoverViewHolder>() {
    /**
     * The queue on screen. [replace] takes effect immediately, so this is always a truthful account
     * of what the carousel is holding, which is what lets the panel tell a new queue from a move
     * along the current one.
     */
    var tracks: List<Track> = emptyList()
        private set

    override fun getItemCount() = tracks.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = CoverViewHolder.from(parent)

    override fun onBindViewHolder(holder: CoverViewHolder, position: Int) {
        holder.bind(tracks[position], onCoverClick)
    }

    /**
     * Swap the queue out for [newTracks], re-seating every page rather than diffing them.
     *
     * A diff would have to be computed off the main thread and applied whenever it finished, which
     * leaves the carousel briefly holding one queue while being told to scroll into another. Being
     * synchronous is what makes it safe to scroll in the same breath.
     */
    fun replace(newTracks: List<Track>) {
        val removed = tracks.size
        notifyItemRangeRemoved(0, removed)
        tracks = newTracks
        notifyItemRangeInserted(0, newTracks.size)
    }
}

/** A [RecyclerView.ViewHolder] that displays a [Track]'s cover. */
class CoverViewHolder private constructor(private val binding: ItemCoverBinding) :
    RecyclerView.ViewHolder(binding.root) {
    /** The track this page currently stands for, used to drop cover loads that arrive too late. */
    private var boundTrackId = -1L


    /**
     * Bind new data to this instance.
     *
     * @param track The new [Track] to bind.
     * @param onCoverClick Called when this cover is clicked.
     */
    fun bind(track: Track, onCoverClick: () -> Unit) {
        val context = binding.root.context
        binding.root.setOnClickListener { onCoverClick() }
        boundTrackId = track.mediaStoreId

        // The page hands its cover over to Glide before this method returns, come what may. A page
        // that leaves its ImageView untouched while art is resolved keeps drawing the cover of
        // whatever track it held before, which is what shows through the carousel for a moment
        // whenever the queue is re-seated under it.
        val knownCoverArt = track.coverArt.ifEmpty { null }
        loadCover(context, knownCoverArt)
        if (knownCoverArt != null) {
            return
        }

        // Only a track carrying no art of its own has to be read off the main thread, and this
        // holder may have been recycled onto another track by the time that lands.
        context.getTrackCoverArt(track) { coverArt ->
            if (boundTrackId == track.mediaStoreId) {
                loadCover(context, coverArt)
            }
        }
    }

    /**
     * Show [coverArt] on this page, or the placeholder when there is none. Loading into the
     * ImageView (rather than into a free-standing callback) is what lets Glide cancel whatever
     * request the view was previously bound to.
     */
    private fun loadCover(context: Context, coverArt: Any?) {
        Glide.with(context)
            .load(coverArt)
            .apply(RequestOptions().centerCrop().error(placeholder(context)))
            .into(binding.cover)
    }

    private fun placeholder(context: Context) = CoverFallbackDrawable(context)

    companion object {
        /**
         * Create a new instance.
         *
         * @param parent The parent to inflate this instance from.
         * @return A new instance.
         */
        fun from(parent: ViewGroup) = CoverViewHolder(
            ItemCoverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }
}
