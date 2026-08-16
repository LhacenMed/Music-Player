package org.fossify.musicplayer.views

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import com.google.android.material.slider.Slider
import org.fossify.musicplayer.databinding.ViewSeekBarBinding
import org.fossify.musicplayer.extensions.formatDurationDs
import kotlin.math.max

/**
 * A wrapper around [Slider] that shows position and duration values and sanitizes input to reduce
 * crashes from invalid values.
 */
class StyledSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ForcedLTRFrameLayout(context, attrs, defStyleAttr),
    Slider.OnSliderTouchListener,
    Slider.OnChangeListener {
    private val binding = ViewSeekBarBinding.inflate(LayoutInflater.from(context), this, true)

    /** The current [Listener] attached to this instance. */
    var listener: Listener? = null

    init {
        binding.seekBarSlider.addOnSliderTouchListener(this)
        binding.seekBarSlider.addOnChangeListener(this)
    }

    /**
     * Tint the played and remaining halves of the track. The wave is drawn from the active tint,
     * so [playedColor] colours both the wave and anything behind it.
     */
    fun setTrackColors(playedColor: Int, remainingColor: Int) {
        binding.seekBarSlider.trackActiveTintList = ColorStateList.valueOf(playedColor)
        binding.seekBarSlider.trackInactiveTintList = ColorStateList.valueOf(remainingColor)
    }

    /** Enables/disables wavy active-track rendering to match playback state. */
    fun setWaveEnabled(enabled: Boolean) {
        binding.seekBarSlider.setWaveEnabled(enabled)
    }

    /**
     * The current position, in deci-seconds (1/10th of a second). This is the current value of the
     * SeekBar and is indicated by the start TextView in the layout.
     */
    var positionDs: Long
        get() = binding.seekBarSlider.value.toLong()
        set(value) {
            // Sanity check 1: Ensure that no negative values are sneaking their way into
            // this component.
            val from = max(value, 0)
            // Sanity check 2: Ensure that this value is within the duration and will not crash
            // the app, and that the user is not currently seeking (which would cause the SeekBar
            // to jump around).
            if (from <= durationDs && !isActivated) {
                binding.seekBarSlider.value = from.toFloat()
                // We would want to keep this in the listener, but the listener only fires when
                // a value changes completely, and sometimes that does not happen with this view.
                binding.seekBarPosition.text = from.formatDurationDs(true)
            }
        }

    /**
     * The current duration, in deci-seconds (1/10th of a second). This is the end value of the
     * SeekBar and is indicated by the end TextView in the layout.
     */
    var durationDs: Long
        get() = binding.seekBarSlider.valueTo.toLong()
        set(value) {
            // Sanity check 1: If this is a value so low that it effectively rounds down to
            // zero, use 1 instead and disable the SeekBar.
            val to = max(value, 1)
            isEnabled = value > 0
            // Sanity check 2: If the current value exceeds the new duration value, clamp it
            // down so that we don't crash and instead have an annoying visual flicker.
            if (positionDs > to) {
                binding.seekBarSlider.value = to.toFloat()
            }
            binding.seekBarSlider.valueTo = to.toFloat()
            binding.seekBarDuration.text = value.formatDurationDs(false)
        }

    override fun onStartTrackingTouch(slider: Slider) {
        // User has begun seeking, place the SeekBar into a "Suspended" mode in which no
        // position updates are sent and is indicated by the position value turning accented.
        isActivated = true
    }

    override fun onStopTrackingTouch(slider: Slider) {
        // End of seek event, send off new value to listener.
        isActivated = false
        listener?.onSeekConfirmed(slider.value.toLong())
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        binding.seekBarPosition.text = value.toLong().formatDurationDs(true)
    }

    /** A listener for SeekBar interactions. */
    interface Listener {
        /**
         * Called when the internal [Slider] was scrubbed to a new position, requesting that a seek
         * be performed.
         *
         * @param positionDs The position to seek to, in deci-seconds (1/10th of a second).
         */
        fun onSeekConfirmed(positionDs: Long)
    }
}
