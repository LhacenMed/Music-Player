package org.fossify.musicplayer.views

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.BackportBottomSheetBehavior
import org.fossify.musicplayer.R
import org.fossify.musicplayer.extensions.systemGestureInsetsCompat
import com.google.android.material.R as MR

/**
 * A BottomSheetBehavior that resolves several issues with the default implementation, including:
 * 1. No reasonable edge-to-edge support.
 * 2. Strange corner radius behaviors.
 * 3. Inability to skip half-expanded state when full-screen.
 */
abstract class BaseBottomSheetBehavior<V : View>(context: Context, attributeSet: AttributeSet?) :
    BackportBottomSheetBehavior<V>(context, attributeSet) {
    private var initialized = false
    private val idealBottomGestureInsets = context.resources.getDimensionPixelSize(R.dimen.spacing_medium)

    init {
        // Disable isFitToContents to make the bottom sheet expand to the top of the screen and
        // not just how much the content takes up.
        isFitToContents = false
    }

    /** Create a background [Drawable] to use for this behavior's child [View]. */
    abstract fun createBackground(context: Context): Drawable

    /** Get the ideal bar height to use before the bar is properly measured. */
    abstract fun getIdealBarHeight(context: Context): Int

    /**
     * Called when window insets are being applied to the [View] this behavior is linked to.
     */
    open fun applyWindowInsets(child: View, insets: WindowInsets): WindowInsets {
        // All sheet behaviors derive their peek height from the size of the "bar" (i.e the
        // first child) plus the gesture insets.
        val gestures = insets.systemGestureInsetsCompat
        val bar = (child as ViewGroup).getChildAt(0)
        peekHeight = if (bar.measuredHeight > 0) {
            bar.measuredHeight + gestures.bottom
        } else {
            getIdealBarHeight(child.context) + gestures.bottom
        }
        return insets
    }

    // Enable experimental settings that allow us to skip the half-expanded state.
    override fun shouldSkipHalfExpandedStateWhenDragging() = true

    override fun shouldExpandOnUpwardDrag(dragDurationMillis: Long, yPositionPercentage: Float) = true

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        val layout = super.onLayoutChild(parent, child, layoutDirection)
        // Don't repeat redundant initialization.
        if (!initialized) {
            child.apply {
                // Set up compat elevation attributes. These are only shown below API 28.
                translationZ = context.resources.getDimension(MR.dimen.m3_sys_elevation_level1)
                // Background differs depending on concrete implementation.
                background = createBackground(context)
                setOnApplyWindowInsetsListener(::applyWindowInsets)
            }
            initialized = true
            peekHeight = getIdealBarHeight(child.context) + idealBottomGestureInsets
        }
        // Sometimes CoordinatorLayout doesn't dispatch window insets to us, likely due to how
        // much we overload it. Ensure that we get them.
        child.requestApplyInsets()
        return layout
    }
}
