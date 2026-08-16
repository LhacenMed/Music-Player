package org.fossify.musicplayer.dialogs

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.menu.MenuBuilder
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.bottomsheet.BackportBottomSheetBehavior
import com.google.android.material.bottomsheet.BackportBottomSheetDialogFragment
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.R
import org.fossify.musicplayer.activities.AlbumsActivity
import org.fossify.musicplayer.activities.SimpleControllerActivity
import org.fossify.musicplayer.activities.TracksActivity
import org.fossify.musicplayer.databinding.DialogTrackMenuBinding
import org.fossify.musicplayer.databinding.ItemMenuOptionBinding
import org.fossify.musicplayer.extensions.addTracksToPlaylist
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.extensions.getPlaybackSurfaceColor
import org.fossify.musicplayer.extensions.getTrackCoverArt
import org.fossify.musicplayer.extensions.shareFiles
import org.fossify.musicplayer.extensions.showTrackProperties
import org.fossify.musicplayer.helpers.ALBUM
import org.fossify.musicplayer.helpers.ARTIST
import org.fossify.musicplayer.models.Track
import org.fossify.musicplayer.views.CoverFallbackDrawable
import com.google.android.material.R as MR

/**
 * The overflow sheet for the track that is currently playing, offering the actions that apply to a
 * single track. Options are inflated from [R.menu.menu_playback_track] so the sheet stays a plain
 * rendering of a menu resource.
 */
class TrackMenuDialog : BackportBottomSheetDialogFragment() {
    private var binding: DialogTrackMenuBinding? = null

    /** The track the sheet was opened for, captured up front so it survives a track change. */
    private lateinit var track: Track

    /** Which options to offer, since a playing track cannot be started again. */
    private var optionsRes = 0

    /** The tracks [track] sits among, which is what Play and Shuffle act on. */
    private var queue = emptyList<Track>()

    // BackportBottomSheetDialog builds its dialog from getTheme(), which is where the edge-to-edge
    // sheet styling has to come from so this behaves like the queue sheet rather than sitting above
    // the navigation bar.
    override fun getTheme() = R.style.ThemeOverlay_TrackMenu_BottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = DialogTrackMenuBinding.inflate(inflater, container, false)
        .also { binding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = binding ?: return
        binding.menuName.text = track.title
        binding.menuInfo.text = track.artist

        val cornerRadius = resources.getDimension(R.dimen.corner_radius_small).toInt()
        val placeholder = CoverFallbackDrawable(requireContext())

        requireContext().getTrackCoverArt(track) { coverArt ->
            Glide.with(this)
                .load(coverArt)
                .apply(RequestOptions().error(placeholder).transform(CenterCrop(), RoundedCorners(cornerRadius)))
                .into(binding.menuCover)
        }

        binding.menuOptionRecycler.adapter = MenuOptionAdapter(inflateOptions(), ::onOptionSelected)
    }

    override fun onStart() {
        super.onStart()
        // The sheet view itself carries the rounded top and fills the whole dialog, including the
        // area uncovered by a predictive back gesture, so the colour belongs here rather than on
        // the content inside it.
        val sheet = dialog?.findViewById<View>(MR.id.design_bottom_sheet) ?: return
        BackportBottomSheetBehavior.from(sheet)
            .setSheetFillColor(ColorStateList.valueOf(requireContext().getPlaybackSurfaceColor()))
    }

    override fun onDestroyView() {
        binding?.menuOptionRecycler?.adapter = null
        binding = null
        super.onDestroyView()
    }

    @Suppress("RestrictedApi")
    private fun inflateOptions(): List<MenuItem> {
        val menu = MenuBuilder(requireContext())
        requireActivity().menuInflater.inflate(optionsRes, menu)
        return menu.visibleItems
    }

    private fun onOptionSelected(item: MenuItem) {
        val activity = requireActivity() as SimpleControllerActivity
        when (item.itemId) {
            R.id.action_play -> activity.prepareAndPlay(queue, queue.indexOfTrack())
            R.id.action_shuffle -> {
                // Shuffle is set first so the order the player deals already accounts for it, and
                // the picked track still leads it. The listener writes the mode back to settings.
                activity.withPlayer { shuffleModeEnabled = true }
                activity.prepareAndPlay(queue, queue.indexOfTrack())
            }

            R.id.action_add_to_playlist -> activity.addTracksToPlaylist(listOf(track)) {}
            R.id.action_play_next -> activity.playNextInQueue(track) {}
            R.id.action_add_to_queue -> activity.addTracksToQueue(listOf(track)) {}
            R.id.action_go_to_artist -> activity.goToArtist()
            R.id.action_go_to_album -> activity.goToAlbum()
            R.id.action_properties -> activity.showTrackProperties(listOf(track))
            R.id.action_share -> activity.shareFiles(listOf(track))
        }

        dismiss()
    }

    /** Where [track] sits in [queue], so playing it keeps the rest of the list around it. */
    private fun List<Track>.indexOfTrack() =
        indexOfFirst { it.mediaStoreId == track.mediaStoreId }.coerceAtLeast(0)

    /** Open the artist behind this track, on the screen that lists their albums. */
    private fun SimpleControllerActivity.goToArtist() {
        ensureBackgroundThread {
            val artist = audioHelper.getArtist(track.artistId) ?: return@ensureBackgroundThread
            runOnUiThread {
                startActivity(
                    Intent(this, AlbumsActivity::class.java)
                        .putExtra(ARTIST, Gson().toJson(artist))
                )
            }
        }
    }

    /** Open the album this track belongs to, on the screen that lists its tracks. */
    private fun SimpleControllerActivity.goToAlbum() {
        ensureBackgroundThread {
            val album = audioHelper.getAlbum(track.albumId) ?: return@ensureBackgroundThread
            runOnUiThread {
                startActivity(
                    Intent(this, TracksActivity::class.java)
                        .putExtra(ALBUM, Gson().toJson(album))
                )
            }
        }
    }

    companion object {
        /** Show the overflow sheet for a [track] listed among [queue], which Play acts on. */
        fun showForTrack(activity: SimpleControllerActivity, track: Track, queue: List<Track>) =
            show(activity, track, R.menu.menu_track, queue)

        /** Show the overflow sheet for the playing track, which cannot be started again. */
        fun showForPlayingTrack(activity: SimpleControllerActivity, track: Track) =
            show(activity, track, R.menu.menu_playback_track, queue = emptyList())

        private fun show(
            activity: SimpleControllerActivity,
            track: Track,
            optionsRes: Int,
            queue: List<Track>
        ) {
            TrackMenuDialog().apply {
                this.track = track
                this.optionsRes = optionsRes
                this.queue = queue
            }.show(activity.supportFragmentManager, TrackMenuDialog::class.java.simpleName)
        }
    }
}

/** Renders each option of the track menu as one row. */
private class MenuOptionAdapter(
    private val options: List<MenuItem>,
    private val onOptionSelected: (MenuItem) -> Unit
) : RecyclerView.Adapter<MenuOptionViewHolder>() {
    override fun getItemCount() = options.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = MenuOptionViewHolder(
        ItemMenuOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: MenuOptionViewHolder, position: Int) {
        holder.bind(options[position], onOptionSelected)
    }
}

private class MenuOptionViewHolder(private val binding: ItemMenuOptionBinding) :
    RecyclerView.ViewHolder(binding.root) {
    fun bind(item: MenuItem, onOptionSelected: (MenuItem) -> Unit) {
        binding.root.apply {
            text = item.title
            setCompoundDrawablesRelativeWithIntrinsicBounds(item.icon, null, null, null)
            setOnClickListener { onOptionSelected(item) }
        }
    }
}
