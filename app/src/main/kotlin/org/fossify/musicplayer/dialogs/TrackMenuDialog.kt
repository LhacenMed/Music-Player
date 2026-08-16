package org.fossify.musicplayer.dialogs

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.menu.MenuBuilder
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.bottomsheet.BackportBottomSheetBehavior
import com.google.android.material.bottomsheet.BackportBottomSheetDialogFragment
import org.fossify.musicplayer.R
import org.fossify.musicplayer.activities.SimpleControllerActivity
import org.fossify.musicplayer.databinding.DialogTrackMenuBinding
import org.fossify.musicplayer.databinding.ItemMenuOptionBinding
import org.fossify.musicplayer.extensions.addTracksToPlaylist
import org.fossify.musicplayer.extensions.getPlaybackSurfaceColor
import org.fossify.musicplayer.extensions.getTrackCoverArt
import org.fossify.musicplayer.extensions.shareFiles
import org.fossify.musicplayer.extensions.showTrackProperties
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
        requireActivity().menuInflater.inflate(R.menu.menu_playback_track, menu)
        return menu.visibleItems
    }

    private fun onOptionSelected(item: MenuItem) {
        val activity = requireActivity() as SimpleControllerActivity
        when (item.itemId) {
            R.id.action_add_to_playlist -> activity.addTracksToPlaylist(listOf(track)) {}
            R.id.action_play_next -> activity.playNextInQueue(track) {}
            R.id.action_add_to_queue -> activity.addTracksToQueue(listOf(track)) {}
            R.id.action_properties -> activity.showTrackProperties(listOf(track))
            R.id.action_share -> activity.shareFiles(listOf(track))
        }

        dismiss()
    }

    companion object {
        /** Show the overflow sheet for [track]. */
        fun show(activity: SimpleControllerActivity, track: Track) {
            TrackMenuDialog().apply { this.track = track }
                .show(activity.supportFragmentManager, TrackMenuDialog::class.java.simpleName)
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
