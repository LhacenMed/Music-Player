package org.fossify.musicplayer.views

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.core.view.children
import com.google.android.material.button.MaterialButtonGroup
import kotlin.math.min
import com.google.android.material.R as MR

/**
 * A [MaterialButtonGroup] that scales playback controls down to actually fit mid-sized screens.
 *
 * M3 Expressive only has "Large" and then massively smaller "Medium"/"Small" size classes, so you
 * either clip on smaller phones or jump to an unusable button row sizing. Fix this by force-scaling
 * the buttons down.
 */
class ScaledPlaybackButtonGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = MR.attr.materialButtonGroupStyle
) : MaterialButtonGroup(context, attrs, defStyleAttr) {
    private val baseSpacing = spacing
    private var lastAppliedSpacing = baseSpacing

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var scale = 1f

        val scaledChildren = children.filterIsInstance<ScaledPlaybackButton>().toList()
        if (scaledChildren.isEmpty()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        // Must equally space/distribute children across the row.
        val rowWidth = scaledChildren.sumOf { it.baseWidth } + baseSpacing * (scaledChildren.size - 1)
        // Must still fit to the tallest child (when scaled).
        val rowHeight = scaledChildren.maxOf { it.baseHeight }

        // Build a scaling coefficient that fits exactly within the bounds but still scales 1:1.
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        if (widthMode != MeasureSpec.UNSPECIFIED && widthSize > 0 && rowWidth > widthSize) {
            scale = min(scale, widthSize.toFloat() / rowWidth)
        }

        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
        if (heightMode != MeasureSpec.UNSPECIFIED && heightSize > 0 && rowHeight > heightSize) {
            scale = min(scale, heightSize.toFloat() / rowHeight)
        }

        val spacing = (baseSpacing * scale).toInt()
        if (lastAppliedSpacing != spacing) {
            lastAppliedSpacing = spacing
            setSpacing(spacing)
        }
        scaledChildren.forEach { it.applyPlaybackScale(scale) }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
