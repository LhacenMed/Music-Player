package org.fossify.musicplayer.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/**
 * A [FrameLayout] that programmatically overrides the child layout to a left-to-right (LTR) layout
 * direction. This is useful for "Timeline" elements that Material Design recommends be LTR in all
 * cases. This layout can only contain one child, to prevent conflicts with other layout components.
 */
open class ForcedLTRFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {
    override fun onFinishInflate() {
        super.onFinishInflate()
        check(childCount == 1) { "This layout should only contain one child" }
        getChildAt(0).layoutDirection = View.LAYOUT_DIRECTION_LTR
    }
}
