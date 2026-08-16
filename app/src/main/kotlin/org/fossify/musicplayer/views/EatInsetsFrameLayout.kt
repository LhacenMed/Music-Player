package org.fossify.musicplayer.views

import android.content.Context
import android.util.AttributeSet
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.annotation.AttrRes

/**
 * A [FrameLayout] that works around the pre-Android 10 behavior of propagating mutated insets to
 * sibling views. Wrap this around views to isolate mutated window insets.
 */
class EatInsetsFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    init {
        clipToPadding = false
    }

    override fun dispatchApplyWindowInsets(insets: WindowInsets): WindowInsets {
        super.dispatchApplyWindowInsets(insets)
        return insets
    }
}
