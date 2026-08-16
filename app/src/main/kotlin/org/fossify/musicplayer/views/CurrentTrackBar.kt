package org.fossify.musicplayer.views

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.provider.MediaStore
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.media3.common.MediaItem
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.LOWER_ALPHA
import org.fossify.musicplayer.R
import org.fossify.musicplayer.databinding.ViewCurrentTrackBarBinding
import org.fossify.musicplayer.extensions.*
import org.fossify.musicplayer.extensions.getPlaybackSurfaceColor

class CurrentTrackBar(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {
    private val binding by viewBinding(ViewCurrentTrackBarBinding::bind)

    fun initialize(togglePlayback: () -> Unit, skipToNext: () -> Unit) {
        binding.currentTrackPlayPause.setOnClickListener { togglePlayback() }
        binding.currentTrackNext.setOnClickListener { skipToNext() }
    }

    fun updateColors() {
        val primary = context.getProperPrimaryColor()
        binding.currentTrackPlayPause.apply {
            backgroundTintList = ColorStateList.valueOf(primary)
            iconTint = ColorStateList.valueOf(primary.getContrastColor())
        }

        binding.currentTrackNext.backgroundTintList =
            ColorStateList.valueOf(context.getPlaybackSurfaceColor())
        binding.currentTrackTitle.setTextColor(context.getTintedTextColor())
        binding.currentTrackProgress.setIndicatorColor(context.getProperPrimaryColor())
        binding.currentTrackProgress.trackColor = context.getProperPrimaryColor().adjustAlpha(LOWER_ALPHA)
    }

    fun updateCurrentTrack(mediaItem: MediaItem?) {
        val track = mediaItem?.toTrack()
        if (track == null) {
            fadeOut()
            return
        } else {
            fadeIn()
        }

        binding.currentTrackTitle.text = track.title
        binding.currentTrackArtist.text = if (track.artist.trim().isNotEmpty() && track.artist != MediaStore.UNKNOWN_STRING) {
            track.artist
        } else {
            ""
        }

        binding.currentTrackProgress.max = track.duration * DECISECONDS_PER_SECOND
        val cornerRadius = resources.getDimension(R.dimen.corner_radius_small).toInt()
        val currentTrackPlaceholder = CoverFallbackDrawable(context)
        val options = RequestOptions()
            .error(currentTrackPlaceholder)
            .transform(CenterCrop(), RoundedCorners(cornerRadius))

        context.getTrackCoverArt(track) { coverArt ->
            (context as? Activity)?.ensureActivityNotDestroyed {
                Glide.with(this)
                    .load(coverArt)
                    .apply(options)
                    .into(binding.currentTrackImage)
            }
        }
    }

    fun updateTrackState(isPlaying: Boolean) {
        // The icon is a checked-state selector, so the button's state drives the artwork.
        binding.currentTrackPlayPause.isChecked = isPlaying
    }

    fun updateProgress(positionMs: Long) {
        binding.currentTrackProgress.progress = (positionMs / MS_PER_DECISECOND).toInt()
    }

    private companion object {
        /** The bar is driven in deci-seconds so it advances smoothly instead of once a second. */
        const val DECISECONDS_PER_SECOND = 10
        const val MS_PER_DECISECOND = 100L
    }
}
