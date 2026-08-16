package org.fossify.musicplayer.views

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.View
import android.view.WindowInsets
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import org.fossify.musicplayer.extensions.getQueueSurfaceColor
import org.fossify.musicplayer.R
import org.fossify.musicplayer.extensions.replaceSystemBarInsetsCompat
import org.fossify.musicplayer.extensions.systemBarInsetsCompat
import com.google.android.material.R as MR

/**
 * The [BaseBottomSheetBehavior] for the queue bottom sheet. This is placed within the playback
 * sheet and automatically arranges itself to show the playback bar at the top.
 */
class QueueBottomSheetBehavior<V : View>(context: Context, attributeSet: AttributeSet?) :
    BaseBottomSheetBehavior<V>(context, attributeSet) {
    private var barHeight = 0
    private var barSpacing = context.resources.getDimensionPixelSize(R.dimen.spacing_small)

    init {
        // Not hide-able (and not programmatically hide-able)
        isHideable = false
    }

    override fun getIdealBarHeight(context: Context) =
        context.resources.getDimensionPixelSize(R.dimen.size_touchable_large)

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        // Pre-calculate expandedOffset before the sheet is positioned by super.onLayoutChild().
        // This ensures the sheet uses the correct offset during configuration changes like
        // split-screen resize, where layout happens before insets are re-applied.
        val effectiveBarHeight = if (barHeight > 0) barHeight else getIdealBarHeight(child.context)
        expandedOffset = effectiveBarHeight + barSpacing
        return super.onLayoutChild(parent, child, layoutDirection)
    }

    override fun layoutDependsOn(parent: CoordinatorLayout, child: V, dependency: View) =
        dependency.id == R.id.current_track_bar

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: V, dependency: View): Boolean {
        val oldHeight = barHeight
        barHeight = dependency.height
        // If bar height changed, force insets to be recalculated with the new value
        // and signal that the child needs to be re-laid out. This handles cases where
        // the sheet was positioned before the bar was measured or during configuration
        // changes (e.g., split-screen resize) where layout order causes stale values.
        if (oldHeight != barHeight && barHeight > 0) {
            child.requestApplyInsets()
            return true
        }
        return false
    }

    override fun createBackground(context: Context) =
        MaterialShapeDrawable.createWithElevationOverlay(context).apply {
            // The queue sheet's background is a static elevated background.
            fillColor = ColorStateList.valueOf(context.getQueueSurfaceColor())
            shapeAppearanceModel = ShapeAppearanceModel.builder(
                context,
                R.style.ShapeAppearance_PlaybackSheet,
                MR.style.ShapeAppearanceOverlay_Material3_Corner_Top
            ).build()
        }

    override fun applyWindowInsets(child: View, insets: WindowInsets): WindowInsets {
        super.applyWindowInsets(child, insets)
        // Offset our expanded panel by the size of the playback bar, as that is shown when
        // we slide up the panel. Use ideal bar height as fallback when the bar hasn't been
        // measured yet (can occur when window insets are applied before onDependentViewChanged).
        val bars = insets.systemBarInsetsCompat
        val effectiveBarHeight = if (barHeight > 0) barHeight else getIdealBarHeight(child.context)
        expandedOffset = effectiveBarHeight + barSpacing
        return insets.replaceSystemBarInsetsCompat(
            bars.left,
            bars.top,
            bars.right,
            expandedOffset + bars.bottom
        )
    }
}
