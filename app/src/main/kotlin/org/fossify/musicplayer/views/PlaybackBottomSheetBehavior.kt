package org.fossify.musicplayer.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import org.fossify.musicplayer.extensions.getPlaybackSurfaceColor
import org.fossify.musicplayer.R
import org.fossify.musicplayer.extensions.replaceSystemBarInsetsCompat
import org.fossify.musicplayer.extensions.systemBarInsetsCompat
import com.google.android.material.R as MR

/**
 * The [BaseBottomSheetBehavior] for the playback bottom sheet. Collapsed it shows the playback bar,
 * expanded it shows the full playback panel.
 */
class PlaybackBottomSheetBehavior<V : View>(context: Context, attributeSet: AttributeSet?) :
    BaseBottomSheetBehavior<V>(context, attributeSet) {
    lateinit var sheetBackgroundDrawable: MaterialShapeDrawable

    fun makeBackgroundDrawable(context: Context) {
        sheetBackgroundDrawable = MaterialShapeDrawable.createWithElevationOverlay(context).apply {
            fillColor = ColorStateList.valueOf(context.getPlaybackSurfaceColor())
            shapeAppearanceModel = ShapeAppearanceModel.builder(
                context,
                R.style.ShapeAppearance_PlaybackSheet,
                MR.style.ShapeAppearanceOverlay_Material3_Corner_Top
            ).build()
        }
    }

    init {
        isHideable = true
    }

    override fun getIdealBarHeight(context: Context) =
        context.resources.getDimensionPixelSize(R.dimen.size_touchable_large)

    // Hack around issue where the playback sheet will try to intercept nested scrolling events
    // before the queue sheet.
    override fun onInterceptTouchEvent(parent: CoordinatorLayout, child: V, event: MotionEvent) =
        super.onInterceptTouchEvent(parent, child, event) && state != STATE_EXPANDED

    // Note: This is an extension to the vendored BottomSheetBehavior
    //
    // Swiping the collapsed playback bar down dismisses it, which is what stops playback. Any
    // gesture that started from the expanded panel — a drag all the way past the bar, or a back
    // gesture — must only take the sheet back to the bar, so the last resting position decides.
    override fun isHideableWhenDragging() = lastStableState == STATE_COLLAPSED

    override fun createBackground(context: Context) = LayerDrawable(
        arrayOf(
            // Add another colored background so that there is always an obscuring
            // element even as the actual "background" element is faded out.
            MaterialShapeDrawable(sheetBackgroundDrawable.shapeAppearanceModel).apply {
                fillColor = sheetBackgroundDrawable.fillColor
            },
            sheetBackgroundDrawable
        )
    )

    override fun applyWindowInsets(child: View, insets: WindowInsets): WindowInsets {
        super.applyWindowInsets(child, insets)
        // Offset our expanded panel by the size of the playback bar, as that is shown when
        // we slide up the panel.
        val bars = insets.systemBarInsetsCompat
        expandedOffset = bars.top
        return insets.replaceSystemBarInsetsCompat(
            bars.left,
            bars.top,
            bars.right,
            expandedOffset + bars.bottom
        )
    }
}
